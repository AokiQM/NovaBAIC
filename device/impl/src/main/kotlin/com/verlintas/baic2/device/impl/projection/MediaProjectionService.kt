/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.device.impl.projection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.verlintas.baic2.device.impl.R
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Foreground service that owns the MediaProjection session (Android 14+
 * requires the service to be running before the projection is used).
 */
class MediaProjectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.projection_title))
            .setContentText(getString(R.string.projection_text))
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            },
        )

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA) as? Intent
                }
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ProjectionHolder.start(applicationContext, resultCode, data)
            }

            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ProjectionHolder.stop()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.projection_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_START = "com.verlintas.baic2.projection.START"
        const val ACTION_STOP = "com.verlintas.baic2.projection.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "baic2_projection"
        private const val NOTIFICATION_ID = 42
    }
}

/** Process-wide projection session shared between the service and capture calls. */
object ProjectionHolder {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0
    private var appContext: Context? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private const val RECORD_MAX_DIMENSION = 1_280
    private const val RECORD_FRAME_RATE = 30
    private const val RECORD_BITRATE = 4_000_000

    fun start(context: Context, resultCode: Int, data: Intent) {
        stop()
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val active = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
            ?: return
        projection = active
        active.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stop()
                }
            },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
        val metrics = context.resources.displayMetrics
        densityDpi = metrics.densityDpi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = createDisplay(active)
        _ready.value = display != null
    }

    private fun createDisplay(active: MediaProjection): VirtualDisplay? = active.createVirtualDisplay(
        "baic2-capture",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader?.surface,
        null,
        null,
    )

    /**
     * Grabs the latest frame. A static screen may not push new frames, so one
     * retry recreates the virtual display to force one.
     */
    suspend fun capture(): ByteArray? = withContext(Dispatchers.Default) {
        val imageReader = reader ?: return@withContext null
        var image = imageReader.acquireLatestImage()
        if (image == null) {
            delay(220)
            image = imageReader.acquireLatestImage()
        }
        if (image == null) {
            val active = projection ?: return@withContext null
            display?.release()
            display = createDisplay(active)
            delay(320)
            image = imageReader.acquireLatestImage()
        }
        image ?: return@withContext null

        image.use { frame ->
            val plane = frame.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val frameWidth = frame.width
            val frameHeight = frame.height
            val paddedWidth = rowStride / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, frameHeight, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val cropped = if (paddedWidth != frameWidth) {
                Bitmap.createBitmap(padded, 0, 0, frameWidth, frameHeight)
            } else {
                padded
            }
            val output = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.PNG, 100, output)
            if (cropped !== padded) cropped.recycle()
            padded.recycle()
            output.toByteArray()
        }
    }

    sealed interface RecordOutcome {
        data class Recorded(
            val filePath: String,
            val durationMs: Long,
            val sizeBytes: Long,
        ) : RecordOutcome

        data class Unavailable(val reason: String) : RecordOutcome
        data class Failed(val reason: String) : RecordOutcome
    }

    /**
     * Records video by temporarily retargeting the persistent virtual
     * display's surface to a MediaRecorder (one VirtualDisplay per token on
     * Android 14+, so a second display is not an option). Screenshots are
     * unavailable while a recording is running; the reader surface is always
     * restored afterwards.
     */
    suspend fun record(durationMs: Long): RecordOutcome = withContext(Dispatchers.IO) {
        val active = projection
        val activeDisplay = display
        val readerSurface = reader?.surface
        val context = appContext
        if (active == null || activeDisplay == null || readerSurface == null || context == null) {
            return@withContext RecordOutcome.Unavailable(
                "Screen capture is not authorized. Take a screenshot (or grant screen analysis) first.",
            )
        }

        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(directory, "rec-${System.currentTimeMillis()}.mp4")
        val recorder = MediaRecorder()
        try {
            val scale = (RECORD_MAX_DIMENSION.toFloat() / max(width, height).coerceAtLeast(1))
                .coerceAtMost(1f)
            val videoWidth = ((width * scale).toInt()).coerceAtLeast(2) / 2 * 2
            val videoHeight = ((height * scale).toInt()).coerceAtLeast(2) / 2 * 2
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(videoWidth, videoHeight)
            recorder.setVideoFrameRate(RECORD_FRAME_RATE)
            recorder.setVideoEncodingBitRate(RECORD_BITRATE)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            activeDisplay.setSurface(recorder.surface)
            recorder.start()
            delay(durationMs)
            recorder.stop()
            RecordOutcome.Recorded(
                filePath = file.absolutePath,
                durationMs = durationMs,
                sizeBytes = file.length(),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { file.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { file.delete() }
            RecordOutcome.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { recorder.release() }
            runCatching { activeDisplay.setSurface(readerSurface) }
        }
    }

    fun stop() {
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        display = null
        reader = null
        projection = null
        _ready.value = false
    }
}
