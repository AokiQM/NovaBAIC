package com.verlintas.baic2.tools

import android.content.Context
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.device.api.AccessibilityBridge
import com.verlintas.baic2.device.api.OcrProvider
import com.verlintas.baic2.device.api.ScreenshotProvider
import kotlinx.serialization.json.JsonObject

/** Android permission probe, injectable so tools stay testable. */
fun interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

class ToolContext(
    val appContext: Context,
    val permissions: PermissionChecker,
    val screenshot: ScreenshotProvider,
    val ocr: OcrProvider,
    val accessibility: AccessibilityBridge,
) {
    fun isGranted(permission: String): Boolean = permissions.isGranted(permission)
}

/**
 * A device capability exposed to the model. Parameters are validated by the
 * tool itself; failures must be actionable.
 */
interface DeviceTool {
    val spec: ToolSpec

    suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult
}
