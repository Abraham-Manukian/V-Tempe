package com.vtempe.server.features.sync.data.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One row per (user, domain) — that domain's synced client state (profile / workoutProgress /
 *  sleep / weight), stored as JSON. The profile is replaced wholesale (last-write-wins); diaries
 *  are JSON objects that devices merge into by key (VT-AUD-057, see mergeDiary). See
 *  [com.vtempe.server.features.sync.api.registerSyncRoutes] for the allowed [domain] values. */
object SyncBlobs : Table("sync_blobs") {
    val userId = varchar("user_id", 128)
    val domain = varchar("domain", 32)
    val payload = text("payload")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, domain)
}

/** Users whose synced data was just erased: pushes are refused until [blockedUntil], so another
 *  device still holding a valid token can't write the data back (VT-AUD-059). Holds no data but
 *  the uid; expired rows are purged on the next deletion. */
object SyncWriteBarriers : Table("sync_write_barriers") {
    val userId = varchar("user_id", 128)
    val blockedUntil = timestamp("blocked_until")

    override val primaryKey = PrimaryKey(userId)
}
