package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val retentionOption: String,
    val expiresAt: Long?,
    val audioFilePath: String?,
    val audioDurationMs: Long,
    val waveformJson: String,
    val originalSegmentsJson: String,
    val englishSegmentsJson: String? = null,
    val hinglishSegmentsJson: String?,
    val languageMode: String,
    val wordChunkMode: String,
    val removeFillerWords: Boolean = false,
    val stripPunctuation: Boolean,
    val textCasing: String,
    val timestampShiftMs: Long,
    val magicContentJson: String?
)
