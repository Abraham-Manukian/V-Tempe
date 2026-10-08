package com.vtempe.server.features.payments.yookassa.data

import kotlinx.serialization.json.Json

private val ledgerJson = Json { encodeDefaults = true }

/**
 * What goes into the payments ledger's `raw_payload` column for a YooKassa payment: only the
 * fields we actually verified and need for audit/disputes (id, status, paid, amount, our own
 * metadata). The webhook body itself is NOT stored — it is unauthenticated and YooKassa's
 * payment object carries data we have no need to keep (card bin/last4/expiry, issuer, payer
 * authorization details).
 */
fun YooKassaPayment.toLedgerPayload(): String =
    ledgerJson.encodeToString(YooKassaPayment.serializer(), this)
