package com.vtempe.server.features.sync.data.repo

import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.vtempe.server.features.sync.domain.port.SyncBlobRecord
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedSyncBlobRepository(private val database: Database) : SyncBlobRepository {

    override suspend fun push(userId: String, domain: String, payload: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            // insertIgnore + unconditional update, same pattern as ExposedEntitlementRepository —
            // avoids "insert, catch unique violation, retry as update" (Postgres aborts the whole
            // transaction on the failed insert, so a caught retry needs a SAVEPOINT it doesn't
            // have). Unlike entitlement's guarded update, sync is unconditionally last-write-wins:
            // there's no "later expiry always wins" invariant here, just "newest push wins".
            SyncBlobs.insertIgnore {
                it[SyncBlobs.userId] = userId
                it[SyncBlobs.domain] = domain
                it[SyncBlobs.payload] = payload
                it[SyncBlobs.updatedAt] = Instant.now()
            }
            SyncBlobs.update({ (SyncBlobs.userId eq userId) and (SyncBlobs.domain eq domain) }) {
                it[SyncBlobs.payload] = payload
                it[SyncBlobs.updatedAt] = Instant.now()
            }
        }
    }

    override suspend fun pullAll(userId: String): Map<String, SyncBlobRecord> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            SyncBlobs.selectAll()
                .where { SyncBlobs.userId eq userId }
                .associate { row ->
                    row[SyncBlobs.domain] to SyncBlobRecord(
                        payload = row[SyncBlobs.payload],
                        updatedAt = row[SyncBlobs.updatedAt]
                    )
                }
        }
}
