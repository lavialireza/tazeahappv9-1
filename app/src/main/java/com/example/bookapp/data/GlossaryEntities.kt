package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Canonical headword; meanings and spellings are separate so homonyms are not flattened. */
@Entity(tableName = "glossary_terms", indices = [Index(value = ["normalizedHeadword"], unique = true), Index("reviewStatus")])
data class GlossaryTermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val headword: String,
    val normalizedHeadword: String,
    val partOfSpeech: String = "",
    val pronunciation: String = "",
    val etymology: String = "",
    val notes: String = "",
    val reviewStatus: String = STATUS_PENDING,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object { const val STATUS_PENDING = "PENDING"; const val STATUS_VERIFIED = "VERIFIED"; const val STATUS_REJECTED = "REJECTED" }
}

@Entity(tableName = "glossary_spellings", foreignKeys = [ForeignKey(entity = GlossaryTermEntity::class, parentColumns = ["id"], childColumns = ["termId"], onDelete = ForeignKey.CASCADE)], indices = [Index("termId"), Index(value = ["normalizedSpelling"], unique = true)])
data class GlossarySpellingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val termId: Long, val spelling: String, val normalizedSpelling: String, val spellingType: String = "VARIANT")

@Entity(tableName = "glossary_meanings", foreignKeys = [ForeignKey(entity = GlossaryTermEntity::class, parentColumns = ["id"], childColumns = ["termId"], onDelete = ForeignKey.CASCADE)], indices = [Index("termId"), Index("reviewStatus")])
data class GlossaryMeaningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val termId: Long,
    val meaning: String,
    val sourceTitle: String = "",
    val sourceAuthor: String = "",
    val sourcePage: String = "",
    val sourceUrl: String = "",
    val usageTaziehUid: String? = null,
    val usageVersionUid: String? = null,
    val usageRoleUid: String? = null,
    val usageSectionUid: String? = null,
    val usageQuote: String = "",
    val reviewStatus: String = GlossaryTermEntity.STATUS_PENDING,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "glossary_history", foreignKeys = [ForeignKey(entity = GlossaryTermEntity::class, parentColumns = ["id"], childColumns = ["termId"], onDelete = ForeignKey.CASCADE)], indices = [Index("termId"), Index("changedAt")])
data class GlossaryHistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val termId: Long, val action: String, val beforeJson: String, val afterJson: String, val changedAt: Long, val actor: String = "ADMIN")
