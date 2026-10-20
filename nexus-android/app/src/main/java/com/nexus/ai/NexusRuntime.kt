package com.nexus.ai

import android.content.Context

object NexusRuntime {
    @Volatile private var engineInstance: LocalLlmEngine? = null

    @JvmStatic
    fun engine(context: Context): LocalLlmEngine {
        return engineInstance ?: synchronized(this) {
            engineInstance ?: LocalLlmEngine(context.applicationContext).also {
                engineInstance = it
            }
        }
    }

    @JvmStatic
    fun hasEngine(): Boolean = engineInstance != null
}
