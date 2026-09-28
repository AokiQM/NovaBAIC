package com.verlintas.baic2.core.data.repository

import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.McpServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class McpServerRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeAll(): Flow<List<McpServer>> =
        db.mcpServerDao().observeAll().map { list -> list.map(mapper::mcpServerToModel) }

    suspend fun getAll(): List<McpServer> =
        db.mcpServerDao().getAll().map(mapper::mcpServerToModel)

    suspend fun add(name: String, url: String): Long =
        db.mcpServerDao().insert(
            mapper.mcpServerToEntity(
                McpServer(
                    name = name,
                    url = url,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )

    suspend fun setEnabled(id: Long, enabled: Boolean) =
        db.mcpServerDao().setEnabled(id, enabled)

    suspend fun delete(id: Long) = db.mcpServerDao().delete(id)
}
