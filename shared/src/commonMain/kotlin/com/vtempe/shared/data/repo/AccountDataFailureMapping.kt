package com.vtempe.shared.data.repo

import com.vtempe.shared.domain.account.AccountDataFailure
import com.vtempe.shared.domain.util.DataResult

/** What a failed account-data request means for the user (VT-AUD-076). */
internal fun DataResult.Failure.toAccountDataFailure(): AccountDataFailure = when (reason) {
    DataResult.Reason.Network, DataResult.Reason.Timeout -> AccountDataFailure.OFFLINE
    DataResult.Reason.Http -> when (code) {
        401 -> AccountDataFailure.SESSION_EXPIRED
        403 -> AccountDataFailure.ACCESS_DENIED
        else -> AccountDataFailure.SERVER
    }
    else -> AccountDataFailure.SERVER
}
