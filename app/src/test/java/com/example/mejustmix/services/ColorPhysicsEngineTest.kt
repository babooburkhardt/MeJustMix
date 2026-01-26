package com.example.mejustmix.services

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Unit tests for the ColorPhysicsEngine Kubelka-Munk implementation.
 * 
 * These tests verify the mathematical correctness of the K-M transformations
 * and ensure that the mixing algorithm produces expected results.
 */
class ColorPhysicsEngineTest {

    private val EPSILON = 0.01f // Tolerance for floating-point comparisons
    
    // ========================================================================
    // K/S CONVERSION TESTS
    // ========================================================================
    
    @Test
    fun `reflectanceToKS returns near zero for high reflectance`() {
        // High reflectance (R≈0.99) should have K/S ≈ 0 (minimal absorption)
        val ks = ColorPhysicsEngine.reflectanceToKS(0.99f)
        assertTrue("K/S for white should be near 0, was $ks", ks < 0.01f)
    }
    
    @Test
    fun `reflectanceToKS returns high value for low reflectance`() {
        // Low reflectance (R≈0.01) should have high K/S (high absorption)
        val ks = ColorPhysicsEngine.reflectanceToKS(0.01f)
        assertTrue("K/S for black should be high, was $ks", ks > 40f)
    }
    
    @Test
    fun `reflectanceToKS returns 0_25 for mid-gray`() {
        // Mid-gray (R=0.5) should have K/S = 0.25
        // Formula: K/S = (1-R)² / (2R) = (0.5)² / (2*0.5) = 0.25 / 1 = 0.25
        val ks = ColorPhysicsEngine.reflectanceToKS(0.5f)
        assertEquals("K/S for 50% gray should be 0.25", 0.25f, ks, EPSILON)
    }
    
    @Test
    fun `ksToReflectance returns 1 for zero KS`() {
        // K/S = 0 means no absorption, so reflectance = 1 (white)
        val r = ColorPhysicsEngine.ksToReflectance(0f)
        assertEquals("Reflectance for K/S=0 should be 1.0", 1f, r, EPSILON)
    }
    
    @Test
    fun `ksToReflectance returns low value for high KS`() {
        // High K/S means high absorption, so low reflectance
        val r = ColorPhysicsEngine.ksToReflectance(10f)
        assertTrue("Reflectance for K/S=10 should be low, was $r", r < 0.15f)
    }
    
    @Test
    fun `ksToReflectance is inverse of reflectanceToKS`() {
        // Test round-trip conversion
        val testValues = listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)
        
        for (r in testValues) {
            val ks = ColorPhysicsEngine.reflectanceToKS(r)
            val rBack = ColorPhysicsEngine.ksToReflectance(ks)
            assertEquals(
                "Round-trip R→K/S→R should preserve value for R=$r",
                r, rBack, EPSILON
            )
        }
    }
    
    // ========================================================================
    // GAMMA CORRECTION TESTS
    // ========================================================================
    
    @Test
    fun `srgbToLinear handles black correctly`() {
        assertEquals(0f, ColorPhysicsEngine.srgbToLinear(0f), EPSILON)
    }
    
    @Test
    fun `srgbToLinear handles white correctly`() {
        assertEquals(1f, ColorPhysicsEngine.srgbToLinear(1f), EPSILON)
    }
    
    @Test
    fun `linearToSrgb is inverse of srgbToLinear`() {
        val testValues = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)
        
        for (srgb in testValues) {
            val linear = ColorPhysicsEngine.srgbToLinear(srgb)
            val srgbBack = ColorPhysicsEngine.linearToSrgb(linear)
            assertEquals(
                "Round-trip sRGB→Linear→sRGB should preserve value for $srgb",
                srgb, srgbBack, EPSILON
            )
        }
    }
    
    @Test
    fun `srgbToLinear produces expected mid-gray`() {
        // sRGB 0.5 (mid-gray on screen) ≈ 0.214 linear (perceptually correct)
        val linear = ColorPhysicsEngine.srgbToLinear(0.5f)
        assertTrue(
            "sRGB 0.5 should be ~0.214 linear, was $linear",
            linear > 0.2f && linear < 0.23f
        )
    }
    
    // ========================================================================
    // PIGMENT MIXING TESTS
    // ========================================================================
    
    @Test
    fun `mixPigments with single pigment returns that pigment`() {
        val cyan = KSColor(ksR = 2.0f, ksG = 0.3f, ksB = 0.1f, s = 1.0f)
        
        val result = ColorPhysicsEngine.mixPigments(
            listOf(cyan),
            listOf(1.0f)
        )
        
        assertEquals(cyan.ksR, result.ksR, EPSILON)
        assertEquals(cyan.ksG, result.ksG, EPSILON)
        assertEquals(cyan.ksB, result.ksB, EPSILON)
    }
    
    @Test
    fun `mixPigments with zero concentration returns neutral`() {
        val cyan = KSColor(ksR = 2.0f, ksG = 0.3f, ksB = 0.1f, s = 1.0f)
        
        val result = ColorPhysicsEngine.mixPigments(
            listOf(cyan),
            listOf(0.0f)
        )
        
        // With zero concentration, should return neutral/default
        assertEquals(0f, result.ksR, EPSILON)
        assertEquals(0f, result.ksG, EPSILON)
        assertEquals(0f, result.ksB, EPSILON)
    }
    
    @Test
    fun `mixPigments produces darker result for complementary colors`() {
        // Mixing cyan (absorbs red) and magenta (absorbs green) should be darker
        val cyan = KSColor(ksR = 5.0f, ksG = 0.1f, ksB = 0.1f, s = 1.0f)
        val magenta = KSColor(ksR = 0.1f, ksG = 5.0f, ksB = 0.1f, s = 1.0f)
        
        val result = ColorPhysicsEngine.mixPigments(
            listOf(cyan, magenta),
            listOf(0.5f, 0.5f)
        )
        
        // Result should absorb both red and green (high K/S for R and G)
        assertTrue("Mixed cyan+magenta should absorb red", result.ksR > 1.0f)
        assertTrue("Mixed cyan+magenta should absorb green", result.ksG > 1.0f)
    }
    
    @Test
    fun `mixPigments with white lightens the result`() {
        val cyan = KSColor(ksR = 5.0f, ksG = 0.1f, ksB = 0.1f, s = 1.0f)
        val white = KSColor(ksR = 0.01f, ksG = 0.01f, ksB = 0.01f, s = 1.0f)
        
        val pureResult = ColorPhysicsEngine.mixPigments(
            listOf(cyan),
            listOf(1.0f)
        )
        
        val mixedWithWhite = ColorPhysicsEngine.mixPigments(
            listOf(cyan, white),
            listOf(0.5f, 0.5f)
        )
        
        // K/S values should be lower (lighter) when mixed with white
        // Note: Due to white scattering boost, the effect should be significant
        assertTrue(
            "Adding white should lower K/S (lighten): pure=${pureResult.ksR}, mixed=${mixedWithWhite.ksR}",
            mixedWithWhite.ksR < pureResult.ksR
        )
    }
}
