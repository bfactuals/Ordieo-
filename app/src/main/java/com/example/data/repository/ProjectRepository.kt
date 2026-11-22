package com.example.data.repository

import android.content.Context
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.media.AudioExtractor
import com.example.data.model.AudioFileItem
import com.example.data.model.AutoCorrectRule
import com.example.data.model.BackupPayload
import com.example.data.model.BackupProjectItem
import com.example.data.model.LanguageMode
import com.example.data.model.MagicContent
import com.example.data.model.ProjectModel
import com.example.data.model.RetentionOption
import com.example.data.model.StorageStats
import com.example.data.model.TextCasing
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordChunkMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val segmentListType = Types.newParameterizedType(List::class.java, TranscriptSegment::class.java)
    private val segmentListAdapter = moshi.adapter<List<TranscriptSegment>>(segmentListType)

    private val floatListType = Types.newParameterizedType(List::class.java, Float::class.javaObjectType)
    private val floatListAdapter = moshi.adapter<List<Float>>(floatListType)

    private val magicAdapter = moshi.adapter(MagicContent::class.java)
    private val backupAdapter = moshi.adapter(BackupPayload::class.java).indent("  ")

    val projectsFlow: Flow<List<ProjectModel>> = projectDao.observeAllProjects().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun getProjectById(id: String): ProjectModel? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)?.toModel()
    }

    suspend fun saveProject(project: ProjectModel) = withContext(Dispatchers.IO) {
        projectDao.upsertProject(project.toEntity())
    }

    suspend fun updateProjectExpiry(projectId: String, option: RetentionOption) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val newExpiresAt = option.computeExpiryTimestamp(now)
        projectDao.updateExpiry(
            id = projectId,
            retentionOption = option.name,
            expiresAt = newExpiresAt,
            updatedAt = now
        )
    }

    suspend fun updateProjectPinned(projectId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        projectDao.updatePinned(
            id = projectId,
            isPinned = isPinned,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateProjectTitle(projectId: String, newTitle: String) = withContext(Dispatchers.IO) {
        projectDao.updateTitle(
            id = projectId,
            title = newTitle.trim().ifBlank { "Untitled" },
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val existing = projectDao.getProjectById(projectId)
        existing?.audioFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        projectDao.deleteById(projectId)
    }

    /**
     * Permanently deletes selected audio files while retaining their project transcripts,
     * and returns count of deleted audio files.
     */
    suspend fun deleteAudioFilesForProjects(projectIds: Set<String>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        val now = System.currentTimeMillis()
        for (id in projectIds) {
            val entity = projectDao.getProjectById(id) ?: continue
            val path = entity.audioFilePath
            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists()) {
                    runCatching { f.delete() }
                }
                projectDao.clearAudioPath(id, now)
                removed++
            }
        }
        removed
    }

    /**
     * Computes real local storage breakdown across audio files and Room transcript records.
     */
    suspend fun computeStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val allEntities = projectDao.getAllProjectsOnce()
        var audioBytes = 0L
        var transcriptBytes = 0L
        val audioItems = mutableListOf<AudioFileItem>()

        for (entity in allEntities) {
            transcriptBytes += entity.title.toByteArray().size.toLong() +
                entity.originalSegmentsJson.toByteArray().size.toLong() +
                (entity.englishSegmentsJson?.toByteArray()?.size?.toLong() ?: 0L) +
                (entity.hinglishSegmentsJson?.toByteArray()?.size?.toLong() ?: 0L) +
                (entity.magicContentJson?.toByteArray()?.size?.toLong() ?: 0L) +
                entity.waveformJson.toByteArray().size.toLong()

            val path = entity.audioFilePath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    val size = file.length()
                    audioBytes += size
                    audioItems.add(
                        AudioFileItem(
                            projectId = entity.id,
                            projectTitle = entity.title,
                            filePath = path,
                            fileName = file.name,
                            sizeBytes = size,
                            updatedAt = entity.updatedAt
                        )
                    )
                }
            }
        }

        StorageStats(
            totalBytes = audioBytes + transcriptBytes,
            audioBytes = audioBytes,
            transcriptBytes = transcriptBytes,
            audioItems = audioItems.sortedByDescending { it.updatedAt }
        )
    }

    /**
     * Exports all saved transcripts and the custom auto-correct dictionary to a JSON backup payload.
     */
    suspend fun exportBackupJson(dictionary: List<AutoCorrectRule>): String = withContext(Dispatchers.IO) {
        val models = projectDao.getAllProjectsOnce().map { it.toModel() }
        val items = models.map { p ->
            BackupProjectItem(
                id = p.id,
                title = p.title,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                isPinned = p.isPinned,
                retentionOption = p.retentionOption.name,
                originalSegments = p.originalSegments,
                englishSegments = p.englishSegments,
                hinglishSegments = p.hinglishSegments,
                languageMode = p.languageMode.name,
                wordChunkMode = p.wordChunkMode.name,
                removeFillerWords = p.removeFillerWords,
                stripPunctuation = p.stripPunctuation,
                textCasing = p.textCasing.name,
                timestampShiftMs = p.timestampShiftMs,
                magicContent = p.magicContent
            )
        }
        val payload = BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            dictionary = dictionary,
            projects = items
        )
        backupAdapter.toJson(payload)
    }

    /**
     * Restores projects from a JSON backup file and returns (restoredProjectCount, importedDictionaryRules).
     */
    suspend fun importBackupJson(rawJson: String): Pair<Int, List<AutoCorrectRule>> = withContext(Dispatchers.IO) {
        val payload = backupAdapter.fromJson(rawJson)
            ?: throw IllegalArgumentException("Invalid backup file format.")

        var importedCount = 0
        for (item in payload.projects) {
            val retention = RetentionOption.fromKey(item.retentionOption)
            val now = System.currentTimeMillis()
            val id = item.id.ifBlank { UUID.randomUUID().toString() }
            val existing = projectDao.getProjectById(id)
            val model = ProjectModel(
                id = id,
                title = item.title.ifBlank { "Restored Transcript" },
                createdAt = item.createdAt.takeIf { it > 0L } ?: now,
                updatedAt = item.updatedAt.takeIf { it > 0L } ?: now,
                isPinned = item.isPinned,
                retentionOption = retention,
                expiresAt = retention.computeExpiryTimestamp(now),
                audioFilePath = existing?.audioFilePath,
                audioDurationMs = existing?.audioDurationMs ?: (item.originalSegments.maxOfOrNull { it.endMs } ?: 0L),
                waveform = existing?.let {
                    runCatching { floatListAdapter.fromJson(it.waveformJson) }.getOrNull()
                } ?: AudioExtractor.defaultWaveform(),
                originalSegments = item.originalSegments,
                englishSegments = item.englishSegments,
                hinglishSegments = item.hinglishSegments,
                languageMode = LanguageMode.fromKey(item.languageMode),
                wordChunkMode = WordChunkMode.fromKey(item.wordChunkMode),
                removeFillerWords = item.removeFillerWords,
                stripPunctuation = item.stripPunctuation,
                textCasing = TextCasing.fromKey(item.textCasing),
                timestampShiftMs = item.timestampShiftMs,
                magicContent = item.magicContent
            )
            projectDao.upsertProject(model.toEntity())
            importedCount++
        }
        Pair(importedCount, payload.dictionary)
    }

    suspend fun purgeExpiredProjects(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expired = projectDao.getExpiredProjects(now)
        expired.forEach { entity ->
            entity.audioFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
            projectDao.deleteById(entity.id)
        }
        expired.size
    }

    suspend fun clearAllStorage() = withContext(Dispatchers.IO) {
        val all = projectDao.getAllProjectsOnce()
        all.forEach { entity ->
            entity.audioFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        projectDao.deleteAll()
        val audioDir = AudioExtractor.getAudioStorageDir(context)
        audioDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun ProjectEntity.toModel(): ProjectModel {
        val origSegments = runCatching { segmentListAdapter.fromJson(originalSegmentsJson) }
            .getOrNull().orEmpty()
        val engSegments = englishSegmentsJson?.let { json ->
            runCatching { segmentListAdapter.fromJson(json) }.getOrNull()
        }
        val hingSegments = hinglishSegmentsJson?.let { json ->
            runCatching { segmentListAdapter.fromJson(json) }.getOrNull()
        }
        val wave = runCatching { floatListAdapter.fromJson(waveformJson) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: AudioExtractor.defaultWaveform()
        val magic = magicContentJson?.let { json ->
            runCatching { magicAdapter.fromJson(json) }.getOrNull()
        }

        return ProjectModel(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            retentionOption = RetentionOption.fromKey(retentionOption),
            expiresAt = expiresAt,
            audioFilePath = audioFilePath?.takeIf { File(it).exists() },
            audioDurationMs = audioDurationMs,
            waveform = wave,
            originalSegments = origSegments,
            englishSegments = engSegments,
            hinglishSegments = hingSegments,
            languageMode = LanguageMode.fromKey(languageMode),
            wordChunkMode = WordChunkMode.fromKey(wordChunkMode),
            removeFillerWords = removeFillerWords,
            stripPunctuation = stripPunctuation,
            textCasing = TextCasing.fromKey(textCasing),
            timestampShiftMs = timestampShiftMs,
            magicContent = magic
        )
    }

    private fun ProjectModel.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            retentionOption = retentionOption.name,
            expiresAt = expiresAt,
            audioFilePath = audioFilePath,
            audioDurationMs = audioDurationMs,
            waveformJson = floatListAdapter.toJson(waveform),
            originalSegmentsJson = segmentListAdapter.toJson(originalSegments),
            englishSegmentsJson = englishSegments?.let { segmentListAdapter.toJson(it) },
            hinglishSegmentsJson = hinglishSegments?.let { segmentListAdapter.toJson(it) },
            languageMode = languageMode.name,
            wordChunkMode = wordChunkMode.name,
            removeFillerWords = removeFillerWords,
            stripPunctuation = stripPunctuation,
            textCasing = textCasing.name,
            timestampShiftMs = timestampShiftMs,
            magicContentJson = magicContent?.let { magicAdapter.toJson(it) }
        )
    }
}
