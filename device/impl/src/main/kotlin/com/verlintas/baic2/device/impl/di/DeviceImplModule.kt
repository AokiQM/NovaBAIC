package com.verlintas.baic2.device.impl.di

import com.verlintas.baic2.device.api.SpeechOutput
import com.verlintas.baic2.device.impl.AndroidSpeechOutput
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
}
