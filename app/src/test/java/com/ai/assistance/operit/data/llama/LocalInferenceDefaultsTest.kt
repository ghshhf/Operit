package com.ai.assistance.operit.data.llama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceDefaultsTest {

    @Test
    fun contextTiersFollowDeviceMemory() {
        // < 6GB visible RAM: a 8192 context would be an OOM waiting to happen.
        assertEquals(2048, LocalInferenceDefaults.contextSizeForTotalMemory(3L * 1024 * 1024 * 1024))
        assertEquals(2048, LocalInferenceDefaults.contextSizeForTotalMemory(5_800_000_000L))
        // 6-12GB: the mid tier.
        assertEquals(4096, LocalInferenceDefaults.contextSizeForTotalMemory(6L * 1024 * 1024 * 1024))
        assertEquals(4096, LocalInferenceDefaults.contextSizeForTotalMemory(11_000_000_000L))
        // 12GB+: the top tier, still conservative because KV is allocated up front.
        assertEquals(8192, LocalInferenceDefaults.contextSizeForTotalMemory(12L * 1024 * 1024 * 1024))
        assertEquals(8192, LocalInferenceDefaults.contextSizeForTotalMemory(16L * 1024 * 1024 * 1024))
    }

    @Test
    fun recommendedThreadCountIsSane() {
        val threads = LocalInferenceDefaults.recommendedThreadCount()
        assertTrue("threads must be >= 1, was $threads", threads >= 1)
        assertTrue(
            "threads must not exceed core count (${Runtime.getRuntime().availableProcessors()}), was $threads",
            threads <= Runtime.getRuntime().availableProcessors()
        )
        assertTrue("threads must not exceed the cap, was $threads", threads <= 8)
    }
}
