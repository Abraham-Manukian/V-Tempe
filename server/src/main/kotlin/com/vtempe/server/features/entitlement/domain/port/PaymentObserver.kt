package com.vtempe.server.features.entitlement.domain.port

import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund

/**
 * Told about every verified provider payment and refund that goes through
 * [com.vtempe.server.features.entitlement.data.service.EntitlementService], whichever provider it
 * came from (referral rewards listen here). Called on EVERY delivery, duplicates included, after
 * the access change is committed, so implementations must be idempotent. An exception propagates
 * to the provider's webhook, which answers with an error so the provider delivers again.
 */
interface PaymentObserver {
    suspend fun onPayment(payment: VerifiedPayment)
    suspend fun onRefund(refund: VerifiedRefund)

    companion object {
        val NONE: PaymentObserver = object : PaymentObserver {
            override suspend fun onPayment(payment: VerifiedPayment) = Unit
            override suspend fun onRefund(refund: VerifiedRefund) = Unit
        }
    }
}
