package com.verlintas.baic2

import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.AuxiliaryTasks
import com.verlintas.baic2.core.engine.ConfirmationGate
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.core.network.provider.ProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Runtime wiring. Tool catalog/runner bindings live in :tools; the
 * confirmation gate is a placeholder until the runtime v2 gate lands (M3).
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentRuntimeModule {

    @Provides
    @Singleton
    fun provideConfirmationGate(): ConfirmationGate = ConfirmationGate { true }

    @Provides
    @Singleton
    fun provideAgentLoop(
        providerFactory: ProviderFactory,
        toolCatalog: ToolCatalog,
        toolRunner: ToolRunner,
        confirmationGate: ConfirmationGate,
    ): AgentLoop = AgentLoop(
        providerFactory = { providerFactory.create(it) },
        toolCatalog = toolCatalog,
        toolRunner = toolRunner,
        confirmationGate = confirmationGate,
    )

    @Provides
    @Singleton
    fun provideAuxiliaryTasks(providerFactory: ProviderFactory): AuxiliaryTasks =
        AuxiliaryTasks(providerFactory = { providerFactory.create(it) })
}
