package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local rehearsal/performance workspace. Contact details stay in the private app database. */
@Entity(tableName = "performance_groups", indices = [Index(value = ["uid"], unique = true), Index(value = ["normalizedName"], unique = true), Index("updatedAt")])
data class PerformanceGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val name: String,
    val normalizedName: String,
    val city: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "group_members",
    foreignKeys = [ForeignKey(entity = PerformanceGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["uid"], unique = true), Index("groupId"), Index(value = ["groupId", "normalizedName"], unique = true), Index("isActive")])
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val groupId: Long,
    val name: String,
    val normalizedName: String,
    val contact: String = "",
    val vocalRange: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "performances",
    foreignKeys = [ForeignKey(entity = PerformanceGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["uid"], unique = true), Index("groupId"), Index("taziehUid"), Index("versionUid"), Index("scheduledAt"), Index("status")])
data class PerformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val groupId: Long,
    val title: String,
    val taziehUid: String = "",
    val versionUid: String = "",
    val scheduledAt: Long? = null,
    val venue: String = "",
    /** PLANNED, REHEARSAL, READY, COMPLETED, CANCELLED */
    val status: String = STATUS_PLANNED,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
) { companion object { const val STATUS_PLANNED = "PLANNED"; const val STATUS_REHEARSAL = "REHEARSAL"; const val STATUS_READY = "READY"; const val STATUS_COMPLETED = "COMPLETED"; const val STATUS_CANCELLED = "CANCELLED" } }

@Entity(tableName = "performance_assignments",
    foreignKeys = [ForeignKey(entity = PerformanceEntity::class, parentColumns = ["id"], childColumns = ["performanceId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = GroupMemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("performanceId"), Index("memberId"), Index(value = ["performanceId", "roleUid", "memberId"], unique = true)])
data class PerformanceAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val performanceId: Long,
    val memberId: Long,
    val roleUid: String,
    val roleTitle: String,
    val isAlternate: Boolean = false,
    val orderIndex: Int = 0,
    val notes: String = "",
    val assignedAt: Long
)

@Entity(tableName = "performance_notes",
    foreignKeys = [ForeignKey(entity = PerformanceEntity::class, parentColumns = ["id"], childColumns = ["performanceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("performanceId"), Index("sectionUid"), Index("updatedAt")])
data class PerformanceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val performanceId: Long,
    val sectionUid: String = "",
    /** DIRECTOR, REHEARSAL, CUE, GENERAL */
    val noteType: String = "DIRECTOR",
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
)

fun normalizePerformanceName(value: String): String = value.trim().replace('ي','ی').replace('ك','ک').replace(Regex("[\\u064B-\\u065F\\u0670]"), "").replace(Regex("[\\u200c\\s]+"), " ").lowercase()
