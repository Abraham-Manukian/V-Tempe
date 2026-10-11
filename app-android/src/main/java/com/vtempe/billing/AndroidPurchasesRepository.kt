package com.vtempe.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.vtempe.shared.domain.repository.PurchasesRepository

class AndroidPurchasesRepository(
    private val appContext: Context
) : PurchasesRepository, PurchasesUpdatedListener {

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .setListener(this)
            // Billing 8+ API (VT-AUD-082): pending purchases are enabled per product type.
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }

    private val connection = BillingConnection(
        isReady = { billingClient.isReady },
        startConnection = { onResult ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    onResult(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                // Also called long after setup, whenever the service drops; only the first
                // report of an attempt counts (see BillingConnection).
                override fun onBillingServiceDisconnected() = onResult(false)
            })
        },
    )

    override suspend fun isSubscriptionActive(): Boolean {
        // Minimal skeleton: connect and query purchases; return false on any failure
        val connected = connection.connect()
        if (!connected) return false
        // For brevity, we donвЂ™t query specific SKUs here; return false as a safe default.
        return false
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        // No-op skeleton. Integrate acknowledgment/consume later.
    }
}


