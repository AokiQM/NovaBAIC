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
    fun readNotificationsTool(): DeviceTool = com.verlintas.baic2.tools.personal.ReadNotificationsTool()

    @Provides
    @IntoSet
    fun searchContactsTool(): DeviceTool = com.verlintas.baic2.tools.personal.SearchContactsTool()

    @Provides
    @IntoSet
    fun sendEmailTool(): DeviceTool = com.verlintas.baic2.tools.personal.SendEmailTool()

    @Provides
    @IntoSet
    fun createCalendarEventTool(): DeviceTool = com.verlintas.baic2.tools.personal.CreateCalendarEventTool()

    @Provides
    @IntoSet
    fun reminderTool(
        scheduler: com.verlintas.baic2.device.api.ReminderScheduler,
    ): DeviceTool = com.verlintas.baic2.tools.personal.ReminderTool(scheduler)

    @Provides
    @IntoSet
    fun webSearchTool(
        fetcher: com.verlintas.baic2.tools.web.WebFetcher,
    ): DeviceTool = com.verlintas.baic2.tools.web.WebSearchTool(fetcher)

    @Provides
    @IntoSet
    fun webReadTool(
        fetcher: com.verlintas.baic2.tools.web.WebFetcher,
    ): DeviceTool = com.verlintas.baic2.tools.web.WebReadTool(fetcher)

    @Provides
    @IntoSet
    fun getWeatherTool(
        fetcher: com.verlintas.baic2.tools.web.WebFetcher,
        json: kotlinx.serialization.json.Json,
    ): DeviceTool = com.verlintas.baic2.tools.web.GetWeatherTool(fetcher, json)

    @Provides
    @IntoSet
    fun fetchRssTool(
        fetcher: com.verlintas.baic2.tools.web.WebFetcher,
    ): DeviceTool = com.verlintas.baic2.tools.web.FetchRssTool(fetcher)

    @Provides
    @IntoSet
    fun downloadFileTool(
        fetcher: com.verlintas.baic2.tools.web.WebFetcher,
    ): DeviceTool = com.verlintas.baic2.tools.files.DownloadFileTool(fetcher)

    @Provides
    @IntoSet
    fun writeDocumentTool(): DeviceTool = com.verlintas.baic2.tools.files.WriteDocumentTool()

    @Provides
    @IntoSet
    fun listFilesTool(): DeviceTool = com.verlintas.baic2.tools.files.ListFilesTool()

    @Provides
    @IntoSet
    fun readTextFileTool(): DeviceTool = com.verlintas.baic2.tools.files.ReadTextFileTool()

    @Provides
    @IntoSet
    fun ocrFileTool(): DeviceTool = com.verlintas.baic2.tools.media.OcrFileTool()

    @Provides
    @IntoSet
    fun generateQrTool(): DeviceTool = com.verlintas.baic2.tools.media.GenerateQrTool()

    @Provides
    @IntoSet
    fun decodeQrTool(): DeviceTool = com.verlintas.baic2.tools.media.DecodeQrTool()

    @Provides
    @IntoSet
    fun getScreenStateTool(): DeviceTool = com.verlintas.baic2.tools.state.GetScreenStateTool()

    @Provides
    @IntoSet
    fun getForegroundAppTool(): DeviceTool = com.verlintas.baic2.tools.state.GetForegroundAppTool()

    @Provides
    @IntoSet
    fun getLocationTool(): DeviceTool = com.verlintas.baic2.tools.state.GetLocationTool()

    @Provides
    @IntoSet
    fun getAppUsageTool(): DeviceTool = com.verlintas.baic2.tools.state.GetAppUsageTool()

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
