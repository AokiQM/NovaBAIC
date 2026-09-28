package com.verlintas.baic2.device.impl.projection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.core.content.ContextCompat
import com.verlintas.baic2.device.api.ScreenshotProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class AndroidScreenshotProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScreenshotProvider {

    override val ready: StateFlow<Boolean> = ProjectionHolder.ready

    override fun createPermissionIntent(): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    override fun onPermissionResult(resultCode: Int, data: Intent): Boolean {
        if (resultCode != Activity.RESULT_OK) return false
        val intent = Intent(context, MediaProjectionService::class.java).apply {
            action = MediaProjectionService.ACTION_START
            putExtra(MediaProjectionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionService.EXTRA_DATA, data)
        }
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    override suspend fun capture(): Result<ByteArray> = withContext(Dispatchers.IO) {
        // The projection service starts asynchronously after the permission
        // result; wait briefly for it to come up before failing.
        if (!ready.value) {
            val becameReady = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                ready.first { it }
            } ?: false
            if (!becameReady) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "screen_capture_not_authorized: ask the user to grant screen capture first",
                    ),
                )
            }
        }
        ProjectionHolder.capture()?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("screen_capture_timeout: no frame received"))
    }

    override fun release() {
        context.stopService(Intent(context, MediaProjectionService::class.java))
    }
}
