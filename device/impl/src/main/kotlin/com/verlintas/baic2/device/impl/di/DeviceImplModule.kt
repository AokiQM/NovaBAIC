package com.verlintas.baic2.device.impl.di

import com.verlintas.baic2.device.api.OcrProvider
import com.verlintas.baic2.device.api.ScreenshotProvider
import com.verlintas.baic2.device.api.SpeechOutput
import com.verlintas.baic2.device.impl.AndroidSpeechOutput
import com.verlintas.baic2.device.impl.ocr.MlKitOcrProvider
import com.verlintas.baic2.device.impl.projection.AndroidScreenshotProvider
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
}
