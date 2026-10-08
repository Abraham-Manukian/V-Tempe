package com.vtempe.shared.domain.util

/**
 * Generic wrapper around remote or cached data fetch results.
 * Guarantees that call sites handle both success and failure paths explicitly.
 */
sealed class DataResult<out T> {
    data class Success<T>(
        val data: T,
        val fromCache: Boolean = false,
        val rawPayload: String? = null
    ) : DataResult<T>()

    data class Failure(
        val reason: Reason,
        val message: String? = null,
        val code: Int? = null,
        val throwable: Throwable? = null,
        val rawPayload: String? = null
    ) : DataResult<Nothing>()

    enum class Reason {
        Network,
        Timeout,
        InvalidFormat,
        Http,
        CacheMissing,
        /** Blocked on the device: the user has not consented to sending health data to the AI. */
        ConsentRequired,
        Unknown
    }
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(
        data = transform(data),
        fromCache = fromCache,
        rawPayload = rawPayload
    )
    is DataResult.Failure -> this
}

fun <T> DataResult<T>.getOrNull(): T? = when (this) {
    is DataResult.Success -> data
    is DataResult.Failure -> null
}

