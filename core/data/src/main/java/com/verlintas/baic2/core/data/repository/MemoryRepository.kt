package com.verlintas.baic2.core.data.repository

import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.db.MemoryEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.MemoryKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemoryRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observe(kind: MemoryKind): Flow<List<Memory>> =
        db.memoryDao().observeByKind(kind.name).map { list -> list.map(mapper::memoryToModel) }

    suspend fun list(kind: MemoryKind): List<Memory> =
        db.memoryDao().getByKind(kind.name).map(mapper::memoryToModel)

    /** Returns false when the exact same fact already exists. */
    suspend fun add(kind: MemoryKind, content: String, conversationId: Long? = null): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return false
        if (db.memoryDao().exists(kind.name, trimmed)) return false
        db.memoryDao().insert(
            MemoryEntity(
                kind = kind.name,
                content = trimmed,
                conversationId = conversationId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun delete(id: Long) = db.memoryDao().delete(id)

    suspend fun clear(kind: MemoryKind) = db.memoryDao().deleteByKind(kind.name)

    suspend fun count(kind: MemoryKind): Int = db.memoryDao().countByKind(kind.name)
}
