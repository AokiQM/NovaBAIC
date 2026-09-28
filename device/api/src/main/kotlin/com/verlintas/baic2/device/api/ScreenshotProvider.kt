package com.verlintas.baic2.device.api

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/**
 * Screen capture through MediaProjection. The permission grant is per-session:
 * [createPermissionIntent] is launched by the UI, and [onPermissionResult]
 * starts the projection service.
 */
interface ScreenshotProvider {
    val ready: StateFlow<Boolean>

    fun createPermissionIntent(): Intent

    /** Returns true when the projection came up. */
    fun onPermissionResult(resultCode: Int, data: Intent): Boolean

    suspend fun capture(): Result<ByteArray>

    fun release()
}
