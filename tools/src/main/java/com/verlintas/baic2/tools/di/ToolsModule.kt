package com.verlintas.baic2.tools.di

import android.content.Context
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.tools.ClipboardGetTool
import com.verlintas.baic2.tools.ClipboardSetTool
import com.verlintas.baic2.tools.ComputeTool
import com.verlintas.baic2.tools.DeviceInfoTool
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.DeviceToolRunner
import com.verlintas.baic2.tools.GetTimeTool
import com.verlintas.baic2.tools.ListInstalledAppsTool
import com.verlintas.baic2.tools.MediaControlTool
import com.verlintas.baic2.tools.NetworkStatusTool
import com.verlintas.baic2.tools.OpenAppTool
import com.verlintas.baic2.tools.OpenDialerTool
import com.verlintas.baic2.tools.OpenSettingsTool
import com.verlintas.baic2.tools.PermissionChecker
import com.verlintas.baic2.tools.SendNotificationTool
import com.verlintas.baic2.tools.SetBrightnessTool
import com.verlintas.baic2.tools.SetFlashlightTool
import com.verlintas.baic2.tools.SetVolumeTool
import com.verlintas.baic2.tools.ShareTextTool
import com.verlintas.baic2.tools.ToolContext
import com.verlintas.baic2.tools.ToolRegistry
import com.verlintas.baic2.tools.VibrateTool
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolsModule {

    @Provides
    @Singleton
    fun provideToolContext(
        @ApplicationContext context: Context,
        screenshotProvider: com.verlintas.baic2.device.api.ScreenshotProvider,
        ocrProvider: com.verlintas.baic2.device.api.OcrProvider,
        accessibilityBridge: com.verlintas.baic2.device.api.AccessibilityBridge,
    ): ToolContext = ToolContext(
        appContext = context,
        permissions = PermissionChecker { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        },
        screenshot = screenshotProvider,
        ocr = ocrProvider,
        accessibility = accessibilityBridge,
    )

    @Provides
    @IntoSet
    fun planUpdateTool(
        planRepository: com.verlintas.baic2.core.data.repository.PlanRepository,
    ): DeviceTool = com.verlintas.baic2.tools.PlanUpdateTool(planRepository)

    @Provides
    @IntoSet
    fun takeScreenshotTool(): DeviceTool = com.verlintas.baic2.tools.TakeScreenshotTool()

    @Provides
    @IntoSet
    fun screenOcrTool(): DeviceTool = com.verlintas.baic2.tools.ScreenOcrTool()

    @Provides
    @IntoSet
    fun uiFindTool(): DeviceTool = com.verlintas.baic2.tools.UiFindTool()

    @Provides
    @IntoSet
    fun uiTapTool(): DeviceTool = com.verlintas.baic2.tools.UiTapTool()

    @Provides
    @IntoSet
    fun uiSwipeTool(): DeviceTool = com.verlintas.baic2.tools.UiSwipeTool()

    @Provides
    @IntoSet
    fun uiTypeTool(): DeviceTool = com.verlintas.baic2.tools.UiTypeTool()

    @Provides
    @IntoSet
    fun uiPressTool(): DeviceTool = com.verlintas.baic2.tools.UiPressTool()

    @Provides
    @IntoSet
    fun getTimeTool(): DeviceTool = GetTimeTool()

    @Provides
    @IntoSet
    fun computeTool(): DeviceTool = ComputeTool()

    @Provides
    @IntoSet
    fun openAppTool(): DeviceTool = OpenAppTool()

    @Provides
    @IntoSet
    fun openSettingsTool(): DeviceTool = OpenSettingsTool()

    @Provides
    @IntoSet
    fun listInstalledAppsTool(): DeviceTool = ListInstalledAppsTool()

    @Provides
    @IntoSet
    fun deviceInfoTool(): DeviceTool = DeviceInfoTool()

    @Provides
    @IntoSet
    fun networkStatusTool(): DeviceTool = NetworkStatusTool()

    @Provides
    @IntoSet
    fun clipboardGetTool(): DeviceTool = ClipboardGetTool()

    @Provides
    @IntoSet
    fun clipboardSetTool(): DeviceTool = ClipboardSetTool()

    @Provides
    @IntoSet
    fun shareTextTool(): DeviceTool = ShareTextTool()

    @Provides
    @IntoSet
    fun openDialerTool(): DeviceTool = OpenDialerTool()

    @Provides
    @IntoSet
    fun vibrateTool(): DeviceTool = VibrateTool()

    @Provides
    @IntoSet
    fun mediaControlTool(): DeviceTool = MediaControlTool()

    @Provides
    @IntoSet
    fun setVolumeTool(): DeviceTool = SetVolumeTool()

    @Provides
    @IntoSet
    fun setBrightnessTool(): DeviceTool = SetBrightnessTool()

    @Provides
    @IntoSet
    fun setFlashlightTool(): DeviceTool = SetFlashlightTool()

    @Provides
    @IntoSet
    fun sendNotificationTool(): DeviceTool = SendNotificationTool()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsBindingModule {

    @Binds
    @Singleton
    abstract fun bindToolCatalog(registry: ToolRegistry): ToolCatalog

    @Binds
    @Singleton
    abstract fun bindToolRunner(runner: DeviceToolRunner): ToolRunner
}
