package com.vtempe.shared.data.account

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.account.LocalDataOwnerStore

class SettingsLocalDataOwnerStore(private val settings: Settings) : LocalDataOwnerStore {

    override fun ownerUid(): String? = settings.getStringOrNull(KEY_OWNER)

    override fun setOwnerUid(uid: String?) {
        if (uid == null) settings.remove(KEY_OWNER) else settings.putString(KEY_OWNER, uid)
    }

    override fun isFullPushPending(): Boolean = settings.getBooleanOrNull(KEY_PUSH_PENDING) == true

    override fun setFullPushPending(pending: Boolean) {
        if (pending) settings.putBoolean(KEY_PUSH_PENDING, true) else settings.remove(KEY_PUSH_PENDING)
    }

    override fun isTracking(): Boolean = settings.getBooleanOrNull(KEY_TRACKING) == true

    override fun startTracking() = settings.putBoolean(KEY_TRACKING, true)

    private companion object {
        const val KEY_OWNER = "account.data.owner.v1"
        const val KEY_PUSH_PENDING = "account.data.push_pending.v1"
        const val KEY_TRACKING = "account.data.tracking.v1"
    }
}
