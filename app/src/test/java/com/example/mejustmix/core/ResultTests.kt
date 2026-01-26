package com.example.mejustmix.core

import com.example.mejustmix.core.error.AppError
import com.example.mejustmix.core.result.AppResult
import com.example.mejustmix.core.result.UiState
import com.example.mejustmix.core.result.toUiState
import com.example.mejustmix.core.result.zip
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for the AppResult and UiState classes.
 */
class ResultTests {

    // ========================================================================
    // AppResult TESTS
    // ========================================================================
    
    @Test
    fun `Success contains correct data`() {
        val result = AppResult.Success("test data")
        
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals("test data", result.data)
    }
    
    @Test
    fun `Error contains correct error`() {
        val error = AppError.Hardware.NotConnected
        val result = AppResult.Error(error)
        
        assertFalse(result.isSuccess)
        assertTrue(result.isError)
        assertFalse(result.isLoading)
        assertEquals(error, result.error)
    }
    
    @Test
    fun `Loading state is correct`() {
        val result = AppResult.Loading
        
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
        assertTrue(result.isLoading)
    }
    
    @Test
    fun `getOrNull returns data for Success`() {
        val result: AppResult<String> = AppResult.Success("test")
        assertEquals("test", result.getOrNull())
    }
    
    @Test
    fun `getOrNull returns null for Error`() {
        val result: AppResult<String> = AppResult.Error(AppError.Unknown())
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `getOrDefault returns data for Success`() {
        val result: AppResult<String> = AppResult.Success("test")
        assertEquals("test", result.getOrDefault("default"))
    }
    
    @Test
    fun `getOrDefault returns default for Error`() {
        val result: AppResult<String> = AppResult.Error(AppError.Unknown())
        assertEquals("default", result.getOrDefault("default"))
    }
    
    @Test
    fun `map transforms Success data`() {
        val result = AppResult.Success(5)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isSuccess)
        assertEquals(10, (mapped as AppResult.Success).data)
    }
    
    @Test
    fun `map passes through Error unchanged`() {
        val error = AppError.Unknown()
        val result: AppResult<Int> = AppResult.Error(error)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isError)
        assertEquals(error, (mapped as AppResult.Error).error)
    }
    
    @Test
    fun `flatMap chains operations`() {
        val result = AppResult.Success(5)
        val flatMapped = result.flatMap { value ->
            if (value > 0) AppResult.Success(value * 2)
            else AppResult.Error(AppError.Unknown("Value must be positive"))
        }
        
        assertTrue(flatMapped.isSuccess)
        assertEquals(10, (flatMapped as AppResult.Success).data)
    }
    
    @Test
    fun `flatMap short-circuits on Error`() {
        val error = AppError.Unknown("first error")
        val result: AppResult<Int> = AppResult.Error(error)
        var wasCalled = false
        
        val flatMapped = result.flatMap { 
            wasCalled = true
            AppResult.Success(it * 2)
        }
        
        assertFalse(wasCalled)
        assertTrue(flatMapped.isError)
    }
    
    @Test
    fun `onSuccess executes for Success`() {
        var executedValue: String? = null
        
        AppResult.Success("test")
            .onSuccess { executedValue = it }
        
        assertEquals("test", executedValue)
    }
    
    @Test
    fun `onSuccess does not execute for Error`() {
        var wasExecuted = false
        
        AppResult.Error(AppError.Unknown())
            .onSuccess { wasExecuted = true }
        
        assertFalse(wasExecuted)
    }
    
    @Test
    fun `onError executes for Error`() {
        var executedError: AppError? = null
        val error = AppError.Unknown("test error")
        
        AppResult.Error(error)
            .onError { executedError = it }
        
        assertEquals(error, executedError)
    }
    
    @Test
    fun `zip combines two Success results`() {
        val result1 = AppResult.Success(1)
        val result2 = AppResult.Success("test")
        
        val zipped = result1.zip(result2)
        
        assertTrue(zipped.isSuccess)
        assertEquals(1 to "test", (zipped as AppResult.Success).data)
    }
    
    @Test
    fun `zip returns first Error if first is Error`() {
        val error = AppError.Unknown("first error")
        val result1: AppResult<Int> = AppResult.Error(error)
        val result2 = AppResult.Success("test")
        
        val zipped = result1.zip(result2)
        
        assertTrue(zipped.isError)
        assertEquals(error, (zipped as AppResult.Error).error)
    }
    
    @Test
    fun `catching wraps successful execution`() {
        val result = AppResult.catching { 
            "computed value"
        }
        
        assertTrue(result.isSuccess)
        assertEquals("computed value", (result as AppResult.Success).data)
    }
    
    @Test
    fun `catching wraps exception as Error`() {
        val result = AppResult.catching { 
            throw RuntimeException("test exception")
        }
        
        assertTrue(result.isError)
    }
    
    // ========================================================================
    // UiState TESTS
    // ========================================================================
    
    @Test
    fun `UiState fromResult converts Success`() {
        val result = AppResult.Success("test")
        val uiState = UiState.fromResult(result)
        
        assertTrue(uiState.isSuccess)
        assertEquals("test", (uiState as UiState.Success).data)
    }
    
    @Test
    fun `UiState fromResult converts Error`() {
        val error = AppError.Unknown()
        val result = AppResult.Error(error)
        val uiState = UiState.fromResult(result)
        
        assertTrue(uiState.isError)
        assertEquals(error, (uiState as UiState.Error).error)
    }
    
    @Test
    fun `UiState fromResult converts Loading`() {
        val result: AppResult<String> = AppResult.Loading
        val uiState = UiState.fromResult(result)
        
        assertTrue(uiState.isLoading)
    }
    
    @Test
    fun `toUiState extension works`() {
        val result = AppResult.Success(42)
        val uiState = result.toUiState()
        
        assertTrue(uiState.isSuccess)
        assertEquals(42, (uiState as UiState.Success).data)
    }
    
    @Test
    fun `UiState dataOrNull returns data for Success`() {
        val uiState: UiState<String> = UiState.Success("test")
        assertEquals("test", uiState.dataOrNull())
    }
    
    @Test
    fun `UiState dataOrNull returns null for non-Success`() {
        val uiState: UiState<String> = UiState.Loading
        assertNull(uiState.dataOrNull())
    }
}
