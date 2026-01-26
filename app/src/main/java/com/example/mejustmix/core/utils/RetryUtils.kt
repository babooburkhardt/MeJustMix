package com.example.mejustmix.core.utils

import com.example.mejustmix.core.error.AppError
import com.example.mejustmix.core.logging.AppLogger
import com.example.mejustmix.core.result.AppResult
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Utility functions for retrying operations with exponential backoff.
 * 
 * ## Usage
 * 
 * ```kotlin
 * // Simple retry
 * val result = retryWithBackoff {
 *     sendGCodeCommand(command)
 * }
 * 
 * // Custom retry configuration
 * val result = retryWithBackoff(
 *     times = 5,
 *     initialDelayMs = 500,
 *     maxDelayMs = 10_000,
 *     factor = 2.0
 * ) {
 *     connectToController()
 * }
 * 
 * // With Result type
 * val result = retryWithBackoffResult(times = 3) {
 *     repository.fetchData()
 * }
 * ```
 */
object RetryUtils {

    /**
     * Retry a suspending operation with exponential backoff.
     * 
     * @param times Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000)
     * @param maxDelayMs Maximum delay between retries (default: 10000)
     * @param factor Multiplier for delay after each attempt (default: 2.0)
     * @param shouldRetry Predicate to determine if we should retry on this exception (default: always retry)
     * @param block The operation to retry
     * @return The result of the operation if successful
     * @throws The last exception if all retries fail
     */
    suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10_000L,
        factor: Double = 2.0,
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null
        
        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e
                
                if (!shouldRetry(e)) {
                    throw e
                }
                
                if (attempt < times - 1) {
                    AppLogger.w("Attempt ${attempt + 1}/$times failed: ${e.message}. Retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = min((currentDelay * factor).toLong(), maxDelayMs)
                }
            }
        }
        
        throw lastException ?: IllegalStateException("Retry failed with no exception")
    }
    
    /**
     * Retry a suspending operation with exponential backoff, returning an AppResult.
     * 
     * Unlike [retryWithBackoff], this version catches exceptions and wraps them
     * in AppResult.Error instead of throwing.
     * 
     * @param times Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000)
     * @param maxDelayMs Maximum delay between retries (default: 10000)
     * @param factor Multiplier for delay after each attempt (default: 2.0)
     * @param shouldRetry Predicate to determine if we should retry on this exception (default: always retry)
     * @param block The operation to retry
     * @return AppResult.Success with the result, or AppResult.Error if all retries fail
     */
    suspend fun <T> retryWithBackoffResult(
        times: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10_000L,
        factor: Double = 2.0,
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend () -> T
    ): AppResult<T> {
        return try {
            AppResult.Success(
                retryWithBackoff(
                    times = times,
                    initialDelayMs = initialDelayMs,
                    maxDelayMs = maxDelayMs,
                    factor = factor,
                    shouldRetry = shouldRetry,
                    block = block
                )
            )
        } catch (e: Throwable) {
            AppResult.Error(AppError.fromException(e))
        }
    }
    
    /**
     * Retry an operation that returns AppResult with exponential backoff.
     * 
     * This version handles operations that already return AppResult, retrying
     * only on Error results.
     * 
     * @param times Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000)
     * @param maxDelayMs Maximum delay between retries (default: 10000)
     * @param factor Multiplier for delay after each attempt (default: 2.0)
     * @param shouldRetry Predicate to determine if we should retry on this error (default: always retry)
     * @param block The operation to retry
     * @return The first Success result, or the last Error if all retries fail
     */
    suspend fun <T> retryResultWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10_000L,
        factor: Double = 2.0,
        shouldRetry: (AppError) -> Boolean = { true },
        block: suspend () -> AppResult<T>
    ): AppResult<T> {
        var currentDelay = initialDelayMs
        var lastError: AppError? = null
        
        repeat(times) { attempt ->
            when (val result = block()) {
                is AppResult.Success -> return result
                is AppResult.Loading -> return result
                is AppResult.Error -> {
                    lastError = result.error
                    
                    if (!shouldRetry(result.error)) {
                        return result
                    }
                    
                    if (attempt < times - 1) {
                        AppLogger.w(
                            "Attempt ${attempt + 1}/$times failed: ${result.error.message}. " +
                            "Retrying in ${currentDelay}ms..."
                        )
                        delay(currentDelay)
                        currentDelay = min((currentDelay * factor).toLong(), maxDelayMs)
                    }
                }
            }
        }
        
        return AppResult.Error(lastError ?: AppError.Unknown("All retry attempts failed"))
    }
}

/**
 * Extension function for easy retrying of suspending blocks.
 */
suspend fun <T> (suspend () -> T).retryWithBackoff(
    times: Int = 3,
    initialDelayMs: Long = 1000L,
    maxDelayMs: Long = 10_000L,
    factor: Double = 2.0
): T = RetryUtils.retryWithBackoff(
    times = times,
    initialDelayMs = initialDelayMs,
    maxDelayMs = maxDelayMs,
    factor = factor,
    block = this
)
