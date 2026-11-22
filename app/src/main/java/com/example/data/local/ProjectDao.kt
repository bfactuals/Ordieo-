package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE expiresAt IS NOT NULL AND expiresAt <= :nowMs")
    suspend fun getExpiredProjects(nowMs: Long): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET retentionOption = :retentionOption, expiresAt = :expiresAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateExpiry(id: String, retentionOption: String, expiresAt: Long?, updatedAt: Long)

    @Query("UPDATE projects SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePinned(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE projects SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE projects SET audioFilePath = NULL, audioDurationMs = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearAudioPath(id: String, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
