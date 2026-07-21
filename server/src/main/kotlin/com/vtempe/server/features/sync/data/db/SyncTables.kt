package com.vtempe.server.features.sync.data.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One row per (user, domain) — the latest pushed snapshot of that domain's local client state
 *  (profile / workoutProgress / sleep / weight), stored opaquely as JSON. Last-write-wins: the
 *  client pushes its full current snapshot after every local change, so this table never needs
 *  to understand the payload's shape, only replace it wholesale. See
 *  [com.vtempe.server.features.sync.api.registerSyncRoutes] for the allowed [domain] values. */
object SyncBlobs : Table("sync_blobs") {
    val userId = varchar("user_id", 128)
    val domain = varchar("domain", 32)
    val payload = text("payload")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, domain)
}
