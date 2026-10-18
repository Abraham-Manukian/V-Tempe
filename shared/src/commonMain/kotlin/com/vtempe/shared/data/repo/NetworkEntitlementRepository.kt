package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.EntitlementDto
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.repository.SubscriptionEntitlement
import com.vtempe.shared.domain.util.DataResult

class NetworkEntitlementRepository(private val api: ApiClient) : EntitlementRepository {
    override suspend fun fetchEntitlement(): DataResult<SubscriptionEntitlement> =
        when (val result = api.getResult<EntitlementDto>("/me/entitlement")) {
            is DataResult.Success -> DataResult.Success(SubscriptionEntitlement(result.data.active, result.data.expiresAt))
            is DataResult.Failure -> result
        }
}
