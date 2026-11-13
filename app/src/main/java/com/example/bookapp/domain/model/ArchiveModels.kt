package com.example.bookapp.domain.model

/** Domain-only archive types. These models intentionally have no Android or Room dependency. */
enum class ReviewState { PENDING_REVIEW, ACCEPTED, REJECTED }
enum class VersionState { DRAFT, REVIEWED, PUBLISHED, LEGACY_UNREVIEWED }
enum class SourceDocumentType { ORIGINAL, REFERENCE, OCR_DERIVED, EXPORT }

data class ArchiveVersion(
    val uid: String,
    val taziehUid: String,
    val versionNumber: Int,
    val title: String,
    val state: VersionState,
    val createdAt: Long,
    val updatedAt: Long,
    val isCurrent: Boolean,
    val changeNote: String,
    val parentVersionUid: String? = null
)

data class ArchiveSourceDocument(
    val uid: String,
    val versionUid: String,
    val fileName: String,
    val mimeType: String,
    val sha256: String,
    val storagePath: String,
    val type: SourceDocumentType,
    val reviewState: ReviewState,
    val importedAt: Long,
    val pageCount: Int?,
    val notes: String
)

data class NewTaziehVersion(
    val taziehUid: String,
    val title: String,
    val changeNote: String = ""
)

data class NewSourceDocument(
    val versionUid: String,
    val fileName: String,
    val mimeType: String,
    val sha256: String,
    val storagePath: String,
    val documentType: SourceDocumentType = SourceDocumentType.ORIGINAL,
    val pageCount: Int? = null,
    val notes: String = ""
)

/** Request to fork an existing revision into a new independently editable revision. */
data class ForkTaziehVersion(
    val sourceVersionUid: String,
    val title: String,
    val changeNote: String = ""
)

/** Counts copied as part of the atomic revision fork. */
data class VersionForkReport(
    val version: ArchiveVersion,
    val rolesCopied: Int,
    val sectionsCopied: Int,
    val footnotesCopied: Int,
    val dialoguesCopied: Int,
    val dialogueTurnsCopied: Int,
    val imagesShared: Int
)
