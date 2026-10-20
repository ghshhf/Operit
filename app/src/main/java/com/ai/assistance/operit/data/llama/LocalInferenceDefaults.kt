package com.ai.assistance.operit.data.llama

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Device-aware default suggestions for local llama.cpp inference.
 *
 * The shipped defaults (4 threads / 2048 context) fit a 2019-era phone; on a
 * modern 8-12 core device with 12GB+ RAM they leave most of the machine idle.
 * These helpers turn "what should the default be" into a measurement instead
 * of a guess, so a fresh config starts near the device's capability.
 */
object LocalInferenceDefaults {

    private const val MAX_RECOMMENDED_THREADS = 8
    private const val CONTEXT_TIER_LOW_BYTES = 6L * 1024 * 1024 * 1024
    private const val CONTEXT_TIER_MID_BYTES = 12L * 1024 * 1024 * 1024

    /**
     * Thread count = number of the fastest CPU cores, capped at 8.
     *
     * Android's scheduler already migrates threads between cores, but extra
     * threads beyond the big-core count mostly add contention on the shared
     * memory bus. The frequency file is readable without any permission; when
     * it is missing (some kernels lock it down) fall back to leaving 2 cores
     * for the system.
     */
    fun recommendedThreadCount(): Int {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val bigCores = countBigCores(coreCount)
        return (if (bigCores > 0) bigCores else coreCount - 2)
            .coerceIn(1, MAX_RECOMMENDED_THREADS)
    }

    private fun countBigCores(coreCount: Int): Int {
        val frequencies = (0 until coreCount).mapNotNull { core ->
            File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq")
                .takeIf { it.canRead() }
                ?.readText()
                ?.trim()
                ?.toLongOrNull()
        }
        if (frequencies.size < coreCount || frequencies.isEmpty()) {
            return 0
        }
        val maxFrequency = frequencies.max()
        return frequencies.count { it >= maxFrequency * 0.85 }
    }

    /**
     * Context size tiered by device RAM.
     *
     * A context of 8192 on a 4GB phone is a guaranteed OOM; 2048 on a 16GB
     * phone wastes capability. Values are deliberately conservative: the KV
     * cache is allocated up front, and background apps still need RAM.
     */
    fun contextSizeForTotalMemory(totalMemoryBytes: Long): Int {
        return when {
            totalMemoryBytes < CONTEXT_TIER_LOW_BYTES -> 2048
            totalMemoryBytes < CONTEXT_TIER_MID_BYTES -> 4096
            else -> 8192
        }
    }

    fun recommendedContextSize(context: Context): Int {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return 2048
        activityManager.getMemoryInfo(memoryInfo)
        // totalMem is the kernel's visible RAM minus carveouts; good enough for tiering.
        return contextSizeForTotalMemory(memoryInfo.totalMem)
    }
}
