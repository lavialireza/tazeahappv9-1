package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A director's independent execution plan for one existing Tazieh (identified by its stable UID). */
@Entity(tableName = "director_plans", indices = [Index(value = ["uid"], unique = true), Index("taziehUid"), Index("status"), Index("updatedAt"), Index("isDeleted")])
data class DirectorPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val taziehUid: String,
    val title: String,
    /** DRAFT, PUBLISHED, ARCHIVED */
    val status: String = STATUS_DRAFT,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft deletion keeps source files and historical content recoverable. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_PUBLISHED = "PUBLISHED"
        const val STATUS_ARCHIVED = "ARCHIVED"
    }
}

@Entity(
    tableName = "director_plan_versions",
    foreignKeys = [ForeignKey(entity = DirectorPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index(value = ["planId", "versionNumber"], unique = true), Index("status")]
)
data class DirectorPlanVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val versionNumber: Int,
    val title: String = "",
    /** DRAFT, PUBLISHED, SUPERSEDED */
    val status: String = STATUS_DRAFT,
    val changeNotes: String = "",
    val createdAt: Long,
    val publishedAt: Long? = null
) {
    companion object { const val STATUS_DRAFT = "DRAFT"; const val STATUS_PUBLISHED = "PUBLISHED"; const val STATUS_SUPERSEDED = "SUPERSEDED" }
}

@Entity(
    tableName = "director_source_files",
    foreignKeys = [ForeignKey(entity = DirectorPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index("sha256"), Index("createdAt")]
)
data class DirectorSourceFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val originalName: String,
    val mimeType: String = "",
    /** App-private file path. The original document is kept unchanged; Room stores only its metadata. */
    val localPath: String,
    val sha256: String = "",
    val pageCount: Int? = null,
    val importMethod: String = "TEXT",
    val extractedText: String = "",
    val createdAt: Long
)

@Entity(
    tableName = "director_turns",
    foreignKeys = [ForeignKey(entity = DirectorPlanVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("versionId"), Index(value = ["versionId", "sequenceIndex"], unique = true), Index(value = ["versionId", "turnNumber"]), Index("continuityGroupUid")]
)
data class DirectorTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val turnNumber: Int,
    val sequenceIndex: Int,
    /** NORMAL or COLLECTIVE */
    val turnType: String = TYPE_NORMAL,
    val title: String = "",
    /** Shared UID connects consecutive text segments that must stay with the same performer. */
    val continuityGroupUid: String = "",
    val isEnabled: Boolean = true,
    val notes: String = ""
) {
    companion object { const val TYPE_NORMAL = "NORMAL"; const val TYPE_COLLECTIVE = "COLLECTIVE" }
}

@Entity(
    tableName = "director_turn_participants",
    foreignKeys = [ForeignKey(entity = DirectorTurnEntity::class, parentColumns = ["id"], childColumns = ["turnId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("turnId"), Index(value = ["turnId", "roleUid"], unique = true), Index("roleUid")]
)
data class DirectorParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: Long,
    val roleUid: String,
    val roleTitle: String,
    val orderIndex: Int = 0,
    val isLead: Boolean = false
)

@Entity(
    tableName = "director_text_segments",
    foreignKeys = [
        ForeignKey(entity = DirectorTurnEntity::class, parentColumns = ["id"], childColumns = ["turnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DirectorSourceFileEntity::class, parentColumns = ["id"], childColumns = ["sourceFileId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("turnId"), Index("sourceFileId"), Index(value = ["turnId", "segmentIndex"], unique = true), Index("sectionUid")]
)
data class DirectorTextSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: Long,
    val segmentIndex: Int,
    val text: String,
    /** Optional link to an existing library section; no existing content is copied or changed. */
    val sectionUid: String? = null,
    val sourceFileId: Long? = null,
    val sourcePageStart: Int? = null,
    val sourcePageEnd: Int? = null
)

@Entity(
    tableName = "director_stage_cues",
    foreignKeys = [
        ForeignKey(entity = DirectorPlanVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DirectorTurnEntity::class, parentColumns = ["id"], childColumns = ["turnId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("versionId"), Index("turnId"), Index(value = ["versionId", "orderIndex"])]
)
data class DirectorStageCueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionId: Long,
    val turnId: Long? = null,
    /** MOVE, MUSIC, LIGHT, IMMEDIATE, PAUSE, OTHER */
    val cueType: String = CUE_OTHER,
    val title: String = "",
    val instruction: String,
    val orderIndex: Int = 0,
    val blocksNextTurn: Boolean = false,
    val continuationRoleUid: String = ""
) {
    companion object { const val CUE_MOVE = "MOVE"; const val CUE_MUSIC = "MUSIC"; const val CUE_LIGHT = "LIGHT"; const val CUE_IMMEDIATE = "IMMEDIATE"; const val CUE_PAUSE = "PAUSE"; const val CUE_OTHER = "OTHER" }
}

@Entity(
    tableName = "director_sessions",
    foreignKeys = [
        ForeignKey(entity = DirectorPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DirectorPlanVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("planId"), Index("versionId"), Index("startedAt"), Index("status")]
)
data class DirectorSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val versionId: Long? = null,
    /** Optional link to an existing performance record, stored as a soft reference. */
    val performanceUid: String = "",
    val title: String = "",
    /** REHEARSAL, LIVE, COMPLETED, PAUSED */
    val status: String = STATUS_REHEARSAL,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String = ""
) {
    companion object { const val STATUS_REHEARSAL = "REHEARSAL"; const val STATUS_LIVE = "LIVE"; const val STATUS_COMPLETED = "COMPLETED"; const val STATUS_PAUSED = "PAUSED" }
}

@Entity(
    tableName = "director_turn_progress",
    foreignKeys = [
        ForeignKey(entity = DirectorSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DirectorTurnEntity::class, parentColumns = ["id"], childColumns = ["turnId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("sessionId"), Index("turnId"), Index(value = ["sessionId", "turnId"], unique = true), Index("status")]
)
data class DirectorTurnProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val turnId: Long,
    /** PENDING, IN_PROGRESS, COMPLETED, SKIPPED */
    val status: String = STATUS_PENDING,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val note: String = ""
) {
    companion object { const val STATUS_PENDING = "PENDING"; const val STATUS_IN_PROGRESS = "IN_PROGRESS"; const val STATUS_COMPLETED = "COMPLETED"; const val STATUS_SKIPPED = "SKIPPED" }
}
