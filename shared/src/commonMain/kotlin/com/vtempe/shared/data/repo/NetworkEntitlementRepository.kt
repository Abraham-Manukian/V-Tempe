package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.EntitlementDto
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.util.DataResult

class NetworkEntitlementRepository(private val api: ApiClient) : EntitlementRepository {
    override suspend fun fetchEntitlement(): DataResult<EntitlementDto> =
        api.getResult("/me/entitlement")
}
