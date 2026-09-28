package com.verlintas.baic2

import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.ConfirmationGate
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.core.network.provider.ProviderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * M1 runtime wiring. Device tools arrive in M3: until then the catalog is
 * empty and any tool call is answered with an actionable failure.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentRuntimeModule {

    @Provides
    @Singleton
    fun provideToolCatalog(): ToolCatalog = EmptyToolCatalog

    @Provides
    @Singleton
    fun provideToolRunner(): ToolRunner = ToolRunner { call ->
        ToolResult.Failure("No device tools are registered yet (${call.name})")
    }

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
}

private object EmptyToolCatalog : ToolCatalog {
    override fun specs(mode: AppMode): List<ToolSpec> = emptyList()

    override fun find(name: String): ToolSpec? = null
}
