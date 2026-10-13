package com.vtempe.server.features.sync.data.repo

import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.vtempe.server.features.sync.data.db.SyncWriteBarriers
import com.vtempe.server.features.sync.domain.port.SyncBlobRecord
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant

class ExposedSyncBlobRepository(private val database: () -> Database) : SyncBlobRepository {

    override suspend fun push(userId: String, domain: String, payload: String, now: Instant): Boolean =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            lockUser(userId)
            val blocked = !SyncWriteBarriers.selectAll()
                .where { (SyncWriteBarriers.userId eq userId) and (SyncWriteBarriers.blockedUntil greater now) }
                .empty()
            if (blocked) return@newSuspendedTransaction false
            // insertIgnore + unconditional update, same pattern as ExposedEntitlementRepository —
            // avoids "insert, catch unique violation, retry as update" (Postgres aborts the whole
            // transaction on the failed insert, so a caught retry needs a SAVEPOINT it doesn't
            // have). Unlike entitlement's guarded update, sync is unconditionally last-write-wins:
            // there's no "later expiry always wins" invariant here, just "newest push wins".
            SyncBlobs.insertIgnore {
                it[SyncBlobs.userId] = userId
                it[SyncBlobs.domain] = domain
                it[SyncBlobs.payload] = payload
                it[SyncBlobs.updatedAt] = now
            }
            SyncBlobs.update({ (SyncBlobs.userId eq userId) and (SyncBlobs.domain eq domain) }) {
                it[SyncBlobs.payload] = payload
                it[SyncBlobs.updatedAt] = now
            }
            true
        }

    override suspend fun pullAll(userId: String): Map<String, SyncBlobRecord> =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            SyncBlobs.selectAll()
                .where { SyncBlobs.userId eq userId }
                .associate { row ->
                    row[SyncBlobs.domain] to SyncBlobRecord(
                        payload = row[SyncBlobs.payload],
                        updatedAt = row[SyncBlobs.updatedAt]
                    )
                }
        }

    override suspend fun deleteAll(userId: String, now: Instant, blockWritesUntil: Instant) {
        newSuspendedTransaction(Dispatchers.IO, database()) {
            lockUser(userId)
            SyncWriteBarriers.deleteWhere { blockedUntil lessEq now }
            SyncWriteBarriers.insertIgnore {
                it[SyncWriteBarriers.userId] = userId
                it[blockedUntil] = blockWritesUntil
            }
            SyncWriteBarriers.update({ SyncWriteBarriers.userId eq userId }) {
                it[blockedUntil] = blockWritesUntil
            }
            SyncBlobs.deleteWhere { SyncBlobs.userId eq userId }
        }
    }

    /**
     * Serializes one user's pushes and deletion until commit (VT-AUD-059). Row locks alone can't:
     * under READ COMMITTED a push that checked "no barrier" before the deletion committed would
     * insert a row the deletion never saw. Other users are not blocked.
     */
    private fun Transaction.lockUser(userId: String) {
        exec("SELECT pg_advisory_xact_lock(${lockKey(userId)})")
    }

    private fun lockKey(userId: String): Long =
        ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())).long
}
