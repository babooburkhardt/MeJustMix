package com.example.mejustmix.core.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.example.mejustmix.core.error.AppError

/**
 * A wrapper that combines UI state with loading and error states.
 * Designed for use with Jetpack Compose and StateFlow.
 * 
 * ## Usage in ViewModel
 * ```kotlin
 * private val _uiState = MutableStateFlow<UiState<MixData>>(UiState.Initial)
 * val uiState: StateFlow<UiState<MixData>> = _uiState.asStateFlow()
 * 
 * fun loadMix() {
 *     viewModelScope.launch {
 *         _uiState.value = UiState.Loading
 *         repository.getMix()
 *             .onSuccess { _uiState.value = UiState.Success(it) }
 *             .onError { _uiState.value = UiState.Error(it) }
 *     }
 * }
 * ```
 * 
 * ## Usage in Composable
 * ```kotlin
 * @Composable
 * fun MixScreen(viewModel: MixViewModel) {
 *     val uiState by viewModel.uiState.collectAsState()
 *     
 *     when (val state = uiState) {
 *         is UiState.Initial -> InitialContent()
 *         is UiState.Loading -> LoadingSpinner()
 *         is UiState.Success -> MixContent(state.data)
 *         is UiState.Error -> ErrorMessage(state.error.getUserMessage())
 *     }
 * }
 * ```
 */
sealed class UiState<out T> {
    
    /**
     * Initial state before any operation has been performed.
     */
    data object Initial : UiState<Nothing>()
    
    /**
     * An operation is in progress.
     */
    data object Loading : UiState<Nothing>()
    
    /**
     * The operation completed successfully with data.
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * The operation failed with an error.
     */
    data class Error(val error: AppError) : UiState<Nothing>()
    
    // ========================================================================
    // QUERY METHODS
    // ========================================================================
    
    val isInitial: Boolean get() = this is Initial
    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    /**
     * Returns the data if in Success state, null otherwise.
     */
    fun dataOrNull(): T? = (this as? Success)?.data
    
    /**
     * Returns the error if in Error state, null otherwise.
     */
    fun errorOrNull(): AppError? = (this as? Error)?.error
    
    // ========================================================================
    // TRANSFORMATION METHODS
    // ========================================================================
    
    /**
     * Transforms the success data.
     */
    inline fun <R> map(transform: (T) -> R): UiState<R> = when (this) {
        is Initial -> Initial
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    /**
     * Executes the appropriate action based on state.
     */
    inline fun fold(
        onInitial: () -> Unit = {},
        onLoading: () -> Unit = {},
        onSuccess: (T) -> Unit = {},
        onError: (AppError) -> Unit = {}
    ) {
        when (this) {
            is Initial -> onInitial()
            is Loading -> onLoading()
            is Success -> onSuccess(data)
            is Error -> onError(error)
        }
    }
    
    companion object {
        /**
         * Creates a UiState from an AppResult.
         */
        fun <T> fromResult(result: AppResult<T>): UiState<T> = when (result) {
            is AppResult.Success -> Success(result.data)
            is AppResult.Error -> Error(result.error)
            is AppResult.Loading -> Loading
        }
    }
}

/**
 * Converts an AppResult to a UiState.
 */
fun <T> AppResult<T>.toUiState(): UiState<T> = UiState.fromResult(this)

/**
 * Converts a Flow of data to a Flow of UiState.
 * Emits Loading before the data and catches errors.
 */
fun <T> Flow<T>.asUiState(): Flow<UiState<T>> = this
    .map<T, UiState<T>> { UiState.Success(it) }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(AppError.fromException(it))) }

/**
 * Converts a Flow of AppResult to a Flow of UiState.
 */
fun <T> Flow<AppResult<T>>.asUiStateFromResult(): Flow<UiState<T>> = this
    .map { it.toUiState() }
    .onStart { emit(UiState.Loading) }
