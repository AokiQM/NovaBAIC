package com.verlintas.baic2.device.impl.di

import com.verlintas.baic2.device.api.AccessibilityBridge
import com.verlintas.baic2.device.api.ReminderScheduler
import com.verlintas.baic2.device.api.OcrProvider
import com.verlintas.baic2.device.api.RunNotifier
import com.verlintas.baic2.device.api.ScreenshotProvider
import com.verlintas.baic2.device.api.SpeechOutput
import com.verlintas.baic2.device.impl.AndroidSpeechOutput
import com.verlintas.baic2.device.impl.a11y.AndroidAccessibilityBridge
import com.verlintas.baic2.device.impl.ocr.MlKitOcrProvider
import com.verlintas.baic2.device.impl.projection.AndroidScreenshotProvider
import com.verlintas.baic2.device.impl.reminder.AndroidReminderScheduler
import com.verlintas.baic2.device.impl.run.AndroidRunNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceImplModule {

    @Binds
    @Singleton
    abstract fun bindSpeechOutput(impl: AndroidSpeechOutput): SpeechOutput

    @Binds
    @Singleton
    abstract fun bindScreenshotProvider(impl: AndroidScreenshotProvider): ScreenshotProvider

    @Binds
    @Singleton
    abstract fun bindOcrProvider(impl: MlKitOcrProvider): OcrProvider

    @Binds
    @Singleton
    abstract fun bindAccessibilityBridge(impl: AndroidAccessibilityBridge): AccessibilityBridge

    @Binds
    @Singleton
    abstract fun bindRunNotifier(impl: AndroidRunNotifier): RunNotifier

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AndroidReminderScheduler): ReminderScheduler
}
