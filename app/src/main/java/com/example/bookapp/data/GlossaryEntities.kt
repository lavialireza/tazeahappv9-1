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
    val externalUid: String? = null,
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
    val externalUid: String? = null,
    val authorOpinion: String = "",
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


@Entity(tableName = "glossary_meaning_sources", foreignKeys = [ForeignKey(entity = GlossaryMeaningEntity::class, parentColumns = ["id"], childColumns = ["meaningId"], onDelete = ForeignKey.CASCADE)], indices = [Index("meaningId"), Index("externalUid")])
data class GlossaryMeaningSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meaningId: Long,
    val externalUid: String? = null,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val year: String = "",
    val page: String = "",
    val url: String = "",
    val sourceType: String = "",
    val note: String = "",
    val reliability: Int? = null
)

@Entity(tableName = "glossary_witnesses", foreignKeys = [ForeignKey(entity = GlossaryMeaningEntity::class, parentColumns = ["id"], childColumns = ["meaningId"], onDelete = ForeignKey.CASCADE)], indices = [Index("meaningId"), Index("externalUid")])
data class GlossaryWitnessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meaningId: Long,
    val externalUid: String? = null,
    val text: String,
    val witnessType: String = "",
    val sourceTitle: String = "",
    val sourcePage: String = "",
    val sourceUrl: String = "",
    val note: String = ""
)

@Entity(tableName = "glossary_history", foreignKeys = [ForeignKey(entity = GlossaryTermEntity::class, parentColumns = ["id"], childColumns = ["termId"], onDelete = ForeignKey.CASCADE)], indices = [Index("termId"), Index("changedAt")])
data class GlossaryHistoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val termId: Long, val action: String, val beforeJson: String, val afterJson: String, val changedAt: Long, val actor: String = "ADMIN")
