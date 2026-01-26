package com.example.mejustmix.core.result

import com.example.mejustmix.core.error.AppError

/**
 * A type-safe result wrapper for operations that can succeed or fail.
 * 
 * This sealed class provides a robust way to handle success and error states
 * without using exceptions for control flow, following Kotlin best practices.
 * 
 * ## Usage Examples
 * 
 * ### Creating Results
 * ```kotlin
 * // Success
 * val result = AppResult.Success(mixedColor)
 * 
 * // Error
 * val error = AppResult.Error(AppError.Hardware.NotConnected)
 * 
 * // Loading state
 * val loading = AppResult.Loading
 * ```
 * 
 * ### Handling Results
 * ```kotlin
 * when (val result = viewModel.calculateMix()) {
 *     is AppResult.Success -> displayColor(result.data)
 *     is AppResult.Error -> showError(result.error.getUserMessage())
 *     is AppResult.Loading -> showLoadingSpinner()
 * }
 * ```
 * 
 * ### Chaining Operations
 * ```kotlin
 * viewModel.calculateMix()
 *     .map { mix -> mix.toGCode() }
 *     .onSuccess { gcode -> sendToController(gcode) }
 *     .onError { error -> logError(error) }
 * ```
 * 
 * @param T The type of data contained in a successful result
 */
sealed class AppResult<out T> {
    
    /**
     * Represents a successful operation with data.
     */
    data class Success<T>(val data: T) : AppResult<T>()
    
    /**
     * Represents a failed operation with an error.
     */
    data class Error(val error: AppError) : AppResult<Nothing>()
    
    /**
     * Represents an operation in progress.
     */
    data object Loading : AppResult<Nothing>()
    
    // ========================================================================
    // QUERY METHODS
    // ========================================================================
    
    /**
     * Returns true if this result represents a success.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this result represents an error.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns true if this result represents a loading state.
     */
    val isLoading: Boolean get() = this is Loading
    
    /**
     * Returns the data if successful, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if successful, or the provided default value.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }
    
    /**
     * Returns the data if successful, or throws an exception.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error.cause ?: Exception(error.message)
        is Loading -> throw IllegalStateException("Result is still loading")
    }
    
    /**
     * Returns the error if present, or null otherwise.
     */
    fun errorOrNull(): AppError? = when (this) {
        is Error -> error
        else -> null
    }
    
    // ========================================================================
    // TRANSFORMATION METHODS
    // ========================================================================
    
    /**
     * Transforms the success data using the provided function.
     * Error and Loading states are passed through unchanged.
     */
    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Transforms the success data using a function that returns another Result.
     * Useful for chaining operations that can each fail.
     */
    inline fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> this
    }
    
    /**
     * Recovers from an error by providing an alternative value.
     */
    inline fun recover(transform: (AppError) -> @UnsafeVariance T): AppResult<T> = when (this) {
        is Success -> this
        is Error -> Success(transform(error))
        is Loading -> this
    }
    
    /**
     * Recovers from an error by providing an alternative Result.
     */
    inline fun recoverWith(transform: (AppError) -> AppResult<@UnsafeVariance T>): AppResult<T> = when (this) {
        is Success -> this
        is Error -> transform(error)
        is Loading -> this
    }
    
    // ========================================================================
    // SIDE EFFECT METHODS
    // ========================================================================
    
    /**
     * Executes the provided action if this is a Success.
     * Returns the original Result for chaining.
     */
    inline fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes the provided action if this is an Error.
     * Returns the original Result for chaining.
     */
    inline fun onError(action: (AppError) -> Unit): AppResult<T> {
        if (this is Error) action(error)
        return this
    }
    
    /**
     * Executes the provided action if this is Loading.
     * Returns the original Result for chaining.
     */
    inline fun onLoading(action: () -> Unit): AppResult<T> {
        if (this is Loading) action()
        return this
    }
    
    /**
     * Executes actions based on the result state.
     */
    inline fun fold(
        onSuccess: (T) -> Unit,
        onError: (AppError) -> Unit,
        onLoading: () -> Unit = {}
    ) {
        when (this) {
            is Success -> onSuccess(data)
            is Error -> onError(error)
            is Loading -> onLoading()
        }
    }
    
    companion object {
        /**
         * Creates a Result by executing a block that might throw.
         * Exceptions are caught and converted to AppError.
         */
        inline fun <T> catching(block: () -> T): AppResult<T> = try {
            Success(block())
        } catch (e: Throwable) {
            Error(AppError.fromException(e))
        }
        
        /**
         * Creates a Result by executing a suspending block that might throw.
         */
        suspend inline fun <T> catchingSuspend(block: suspend () -> T): AppResult<T> = try {
            Success(block())
        } catch (e: Throwable) {
            Error(AppError.fromException(e))
        }
    }
}

/**
 * Combines two Results into a single Result containing a Pair.
 * If either Result is an error, returns the first error encountered.
 */
fun <A, B> AppResult<A>.zip(other: AppResult<B>): AppResult<Pair<A, B>> = when (this) {
    is AppResult.Success -> when (other) {
        is AppResult.Success -> AppResult.Success(data to other.data)
        is AppResult.Error -> other
        is AppResult.Loading -> other
    }
    is AppResult.Error -> this
    is AppResult.Loading -> this
}

/**
 * Combines multiple Results into a single Result containing a List.
 * If any Result is an error, returns the first error encountered.
 */
fun <T> List<AppResult<T>>.sequence(): AppResult<List<T>> {
    val results = mutableListOf<T>()
    for (result in this) {
        when (result) {
            is AppResult.Success -> results.add(result.data)
            is AppResult.Error -> return result
            is AppResult.Loading -> return result
        }
    }
    return AppResult.Success(results)
}
