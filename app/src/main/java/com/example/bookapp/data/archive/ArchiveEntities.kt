package com.example.bookapp.data.archive

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A named, immutable-in-principle revision of a Tazieh text. */
@Entity(
    tableName = "tazieh_versions",
    foreignKeys = [
        ForeignKey(
            entity = com.example.bookapp.data.TaziehEntity::class,
            parentColumns = ["uid"],
            childColumns = ["taziehUid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taziehUid"), Index(value = ["taziehUid", "versionNumber"], unique = true), Index(value = ["uid"], unique = true), Index("parentVersionUid")]
)
data class TaziehVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val taziehUid: String,
    val versionNumber: Int,
    val title: String,
    /** DRAFT, REVIEWED, PUBLISHED, or LEGACY_UNREVIEWED. */
    val status: String = STATUS_DRAFT,
    val createdAt: Long,
    val updatedAt: Long,
    val isCurrent: Boolean = false,
    val changeNote: String = "",
    val parentVersionUid: String? = null
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_REVIEWED = "REVIEWED"
        const val STATUS_PUBLISHED = "PUBLISHED"
        const val STATUS_LEGACY_UNREVIEWED = "LEGACY_UNREVIEWED"
    }
}

/** Metadata for an original or derived Word/PDF/image source; file bytes live outside Room. */
@Entity(
    tableName = "source_documents",
    foreignKeys = [
        ForeignKey(
            entity = TaziehVersionEntity::class,
            parentColumns = ["uid"],
            childColumns = ["versionUid"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("versionUid"), Index(value = ["uid"], unique = true), Index("sha256")]
)
data class SourceDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val versionUid: String,
    val fileName: String,
    val mimeType: String,
    val sha256: String,
    val storagePath: String,
    /** ORIGINAL, REFERENCE, OCR_DERIVED, or EXPORT. */
    val documentType: String = TYPE_ORIGINAL,
    /** PENDING_REVIEW, ACCEPTED, or REJECTED. */
    val reviewStatus: String = REVIEW_PENDING,
    val importedAt: Long,
    val pageCount: Int? = null,
    val notes: String = ""
) {
    companion object {
        const val TYPE_ORIGINAL = "ORIGINAL"
        const val TYPE_REFERENCE = "REFERENCE"
        const val TYPE_OCR_DERIVED = "OCR_DERIVED"
        const val TYPE_EXPORT = "EXPORT"
        const val REVIEW_PENDING = "PENDING_REVIEW"
        const val REVIEW_ACCEPTED = "ACCEPTED"
        const val REVIEW_REJECTED = "REJECTED"
    }
}
