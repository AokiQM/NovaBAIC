package com.verlintas.baic2

import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.AuxiliaryTasks
import com.verlintas.baic2.core.engine.ConfirmationGate
import com.verlintas.baic2.core.engine.ConfirmationQueue
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
 * confirmation gate is backed by a queue the chat UI answers.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentRuntimeModule {

    @Provides
    @Singleton
    fun provideConfirmationQueue(): ConfirmationQueue = ConfirmationQueue()

    @Provides
    @Singleton
    fun provideConfirmationGate(queue: ConfirmationQueue): ConfirmationGate =
        ConfirmationGate { call -> queue.confirm(call) }

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
