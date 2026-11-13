package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Unified, reviewable knowledge records for people, events, places and bibliographic sources. */
@Entity(tableName = "encyclopedia_records", indices = [Index("kind"), Index("reviewStatus"), Index(value = ["kind", "normalizedTitle"], unique = true), Index("updatedAt")])
data class EncyclopediaRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    /** PERSON, EVENT, PLACE, SOURCE */
    val kind: String,
    val title: String,
    val normalizedTitle: String,
    val aliases: String = "",
    val summary: String = "",
    val details: String = "",
    val dateLabel: String = "",
    val placeLabel: String = "",
    val sourceCitation: String = "",
    val externalUrl: String = "",
    val reviewStatus: String = PENDING,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val PERSON = "PERSON"; const val EVENT = "EVENT"; const val PLACE = "PLACE"; const val SOURCE = "SOURCE"
        const val PENDING = "PENDING"; const val VERIFIED = "VERIFIED"; const val REJECTED = "REJECTED"
    }
}

@Entity(tableName = "encyclopedia_links",
    foreignKeys = [ForeignKey(entity = EncyclopediaRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordId"), Index("taziehUid"), Index("versionUid"), Index("roleUid"), Index("sectionUid"), Index(value = ["recordId", "taziehUid", "versionUid", "roleUid", "sectionUid"], unique = true)])
data class EncyclopediaLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val relationType: String = "RELATED_TO",
    val taziehUid: String = "",
    val versionUid: String = "",
    val roleUid: String = "",
    val sectionUid: String = "",
    val note: String = ""
)

@Entity(tableName = "encyclopedia_history",
    foreignKeys = [ForeignKey(entity = EncyclopediaRecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordId"), Index("changedAt")])
data class EncyclopediaHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val action: String,
    val beforeJson: String,
    val afterJson: String,
    val changedAt: Long,
    val actor: String = "ADMIN"
)

fun normalizeEncyclopediaText(value: String): String = value.trim()
    .replace('ي', 'ی').replace('ك', 'ک').replace('ى', 'ی')
    .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
    .replace(Regex("[\\u200c\\s]+"), " ").lowercase()
