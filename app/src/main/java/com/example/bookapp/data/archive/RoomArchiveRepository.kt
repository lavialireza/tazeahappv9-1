package com.example.bookapp.data.archive

import androidx.room.withTransaction
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.domain.model.ArchiveSourceDocument
import com.example.bookapp.domain.model.ArchiveVersion
import com.example.bookapp.domain.model.NewSourceDocument
import com.example.bookapp.domain.model.NewTaziehVersion
import com.example.bookapp.domain.model.ForkTaziehVersion
import com.example.bookapp.domain.model.VersionForkReport
import com.example.bookapp.data.RoleEntity
import com.example.bookapp.data.SectionEntity
import com.example.bookapp.data.FootnoteEntity
import com.example.bookapp.data.DialogueEntity
import com.example.bookapp.data.DialogueTurnEntity
import com.example.bookapp.data.TaziehImageEntity
import com.example.bookapp.domain.model.ReviewState
import com.example.bookapp.domain.model.SourceDocumentType
import com.example.bookapp.domain.model.VersionState
import com.example.bookapp.domain.repository.ArchiveRepository
import com.example.bookapp.domain.usecase.ArchiveValidation
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Room adapter. Room entities never escape this data-layer boundary. */
class RoomArchiveRepository(private val database: AppDatabase) : ArchiveRepository {
    private val dao get() = database.archiveDao()

    override suspend fun versions(taziehUid: String): List<ArchiveVersion> =
        dao.versionsForTazieh(taziehUid).map(TaziehVersionEntity::toDomain)

    override suspend fun createVersion(input: NewTaziehVersion): Result<ArchiveVersion> = safeResult {
        database.withTransaction {
            require(database.taziehDao().getByUid(input.taziehUid) != null) { "تعزیه موردنظر در آرشیو پیدا نشد." }
            val now = System.currentTimeMillis()
            val entity = TaziehVersionEntity(
                uid = "version-${UUID.randomUUID()}",
                taziehUid = input.taziehUid,
                versionNumber = dao.maxVersionNumber(input.taziehUid) + 1,
                title = input.title,
                status = TaziehVersionEntity.STATUS_DRAFT,
                createdAt = now,
                updatedAt = now,
                isCurrent = false,
                changeNote = input.changeNote
            )
            dao.insertVersion(entity)
            entity.toDomain()
        }
    }

    /**
     * Forks a revision atomically. Every content row receives a new stable UID and
     * new local primary key; user-personal reading data is intentionally not copied.
     */
    override suspend fun forkVersion(input: ForkTaziehVersion): Result<VersionForkReport> = safeResult {
        database.withTransaction {
            val source = dao.versionByUid(input.sourceVersionUid)
                ?: error("نسخه مبدأ برای ایجاد نسخه جدید پیدا نشد.")
            require(input.title.isNotBlank()) { "عنوان نسخه جدید الزامی است." }
            val tazieh = database.taziehDao().getByUid(source.taziehUid)
                ?: error("تعزیه مربوط به نسخه مبدأ پیدا نشد.")
            val now = System.currentTimeMillis()
            val childUid = "version-${UUID.randomUUID()}"
            val child = TaziehVersionEntity(
                uid = childUid,
                taziehUid = source.taziehUid,
                versionNumber = dao.maxVersionNumber(source.taziehUid) + 1,
                title = input.title.trim(),
                status = TaziehVersionEntity.STATUS_DRAFT,
                createdAt = now,
                updatedAt = now,
                isCurrent = true,
                changeNote = input.changeNote.trim().ifBlank { "ایجاد نسخه مستقل از نسخه ${source.versionNumber}" },
                parentVersionUid = source.uid
            )
            // The current marker is switched only inside this transaction.
            dao.clearCurrentVersion(source.taziehUid)
            dao.insertVersion(child)

            val roleMap = linkedMapOf<Long, Long>()
            val sectionMap = linkedMapOf<Long, Long>()
            val dialogueMap = linkedMapOf<Long, Long>()
            val oldRoles = database.roleDao().getByVersionUid(source.uid)
            var sectionCount = 0
            var footnoteCount = 0
            var dialogueTurnCount = 0

            for (oldRole in oldRoles) {
                val newRoleId = database.roleDao().insert(
                    RoleEntity(
                        taziehId = tazieh.id,
                        title = oldRole.title,
                        orderIndex = oldRole.orderIndex,
                        uid = "role-${UUID.randomUUID()}",
                        versionUid = childUid
                    )
                )
                roleMap[oldRole.id] = newRoleId

                for (oldSection in database.sectionDao().getByRole(oldRole.id)) {
                    val newSectionId = database.sectionDao().insert(
                        SectionEntity(
                            roleId = newRoleId,
                            orderIndex = oldSection.orderIndex,
                            title = oldSection.title,
                            content = oldSection.content,
                            audioUrl = oldSection.audioUrl,
                            uid = "section-${UUID.randomUUID()}",
                            sourceUid = oldSection.sourceUid
                        )
                    )
                    sectionMap[oldSection.id] = newSectionId
                    sectionCount++
                    for (footnote in database.footnoteDao().getBySection(oldSection.id)) {
                        database.footnoteDao().insert(
                            FootnoteEntity(
                                sectionId = newSectionId,
                                term = footnote.term,
                                explanation = footnote.explanation,
                                uid = "footnote-${UUID.randomUUID()}"
                            )
                        )
                        footnoteCount++
                    }
                }
            }

            // Older dialogue records may have an empty versionUid. Resolve ownership
            // from their linked sections so legacy content is still forked correctly.
            for (oldDialogue in database.dialogueDao().getByTazieh(tazieh.id)) {
                val turnsToCopy = database.dialogueTurnDao().getByDialogue(oldDialogue.id)
                    .filter { sectionMap.containsKey(it.sectionId) }
                if (turnsToCopy.isEmpty()) continue
                val newDialogueId = database.dialogueDao().insert(
                    DialogueEntity(
                        taziehId = tazieh.id,
                        title = oldDialogue.title,
                        uid = "dialogue-${UUID.randomUUID()}",
                        versionUid = childUid
                    )
                )
                dialogueMap[oldDialogue.id] = newDialogueId
                for (turn in turnsToCopy) {
                    val newSectionId = sectionMap.getValue(turn.sectionId)
                    database.dialogueTurnDao().insert(
                        DialogueTurnEntity(
                            dialogueId = newDialogueId,
                            sectionId = newSectionId,
                            orderIndex = turn.orderIndex,
                            uid = "dialogue-turn-${UUID.randomUUID()}"
                        )
                    )
                    dialogueTurnCount++
                }
            }

            // Tazieh images are intentionally shared assets in the current schema:
            // they are not duplicated because doing so would create duplicate gallery entries.

            // Source document bytes are immutable and remain in their existing storage location.
            // Only metadata is forked; the same SHA-256 and path prove which original was used.
            for (document in dao.documentsForVersion(source.uid)) {
                dao.insertSourceDocument(
                    document.copy(
                        id = 0,
                        uid = "source-${UUID.randomUUID()}",
                        versionUid = childUid,
                        importedAt = now,
                        reviewStatus = SourceDocumentEntity.REVIEW_PENDING,
                        notes = listOf(document.notes, "فراداده کپی‌شده از نسخه ${source.versionNumber}").filter { it.isNotBlank() }.joinToString(" | ")
                    )
                )
            }

            require(dao.markCurrentVersion(childUid) == 1) { "تعیین نسخه جاری ناموفق بود." }
            val resultVersion = (dao.versionByUid(childUid) ?: error("نسخه جدید پس از ثبت پیدا نشد.")).toDomain()
            VersionForkReport(
                version = resultVersion,
                rolesCopied = roleMap.size,
                sectionsCopied = sectionCount,
                footnotesCopied = footnoteCount,
                dialoguesCopied = dialogueMap.size,
                dialogueTurnsCopied = dialogueTurnCount,
                imagesShared = database.taziehImageDao().getByTazieh(tazieh.id).size
            )
        }
    }

    override suspend fun addSource(input: NewSourceDocument): Result<ArchiveSourceDocument> = safeResult {
        require(dao.versionByUid(input.versionUid) != null) { "نسخه تعزیه برای ثبت سند پیدا نشد." }
        val entity = SourceDocumentEntity(
            uid = "source-${UUID.randomUUID()}",
            versionUid = input.versionUid,
            fileName = input.fileName,
            mimeType = input.mimeType.ifBlank { "application/octet-stream" },
            sha256 = input.sha256.lowercase(),
            storagePath = input.storagePath,
            documentType = input.documentType.name,
            reviewStatus = SourceDocumentEntity.REVIEW_PENDING,
            importedAt = System.currentTimeMillis(),
            pageCount = input.pageCount,
            notes = input.notes
        )
        dao.insertSourceDocument(entity)
        entity.toDomain()
    }

    override suspend fun sources(versionUid: String): List<ArchiveSourceDocument> =
        dao.documentsForVersion(versionUid).map(SourceDocumentEntity::toDomain)

    override suspend fun updateVersionState(
        versionUid: String,
        state: VersionState,
        changeNote: String
    ): Result<ArchiveVersion> = safeResult {
        database.withTransaction {
            val current = dao.versionByUid(versionUid) ?: error("نسخه موردنظر پیدا نشد.")
            val currentState = runCatching { VersionState.valueOf(current.status) }
                .getOrDefault(VersionState.LEGACY_UNREVIEWED)
            require(ArchiveValidation.isAllowedVersionTransition(currentState, state)) {
                "تغییر وضعیت از ${currentState.name} به ${state.name} مجاز نیست؛ برای اصلاح نسخه منتشرشده، نسخه جدید بسازید."
            }
            val effectiveNote = changeNote.ifBlank { current.changeNote }
            val affected = dao.updateVersionState(versionUid, state.name, System.currentTimeMillis(), effectiveNote)
            require(affected == 1) { "ذخیره وضعیت نسخه ناموفق بود." }
            (dao.versionByUid(versionUid) ?: error("بازیابی نسخه پس از تغییر ناموفق بود.")).toDomain()
        }
    }

    override suspend fun updateSourceReview(
        uid: String,
        state: ReviewState,
        notes: String
    ): Result<ArchiveSourceDocument> = safeResult {
        database.withTransaction {
            val existing = dao.documentByUid(uid) ?: error("سند موردنظر پیدا نشد.")
            val effectiveNotes = notes.ifBlank { existing.notes }
            val updated = dao.updateDocumentReview(uid, state.name, effectiveNotes)
            require(updated == 1) { "ذخیره وضعیت بررسی سند ناموفق بود." }
            (dao.documentByUid(uid) ?: error("بازیابی سند پس از تغییر ناموفق بود.")).toDomain()
        }
    }
}

private fun TaziehVersionEntity.toDomain() = ArchiveVersion(
    uid = uid,
    taziehUid = taziehUid,
    versionNumber = versionNumber,
    title = title,
    state = runCatching { VersionState.valueOf(status) }.getOrDefault(VersionState.LEGACY_UNREVIEWED),
    createdAt = createdAt,
    updatedAt = updatedAt,
    isCurrent = isCurrent,
    changeNote = changeNote,
    parentVersionUid = parentVersionUid
)

private fun SourceDocumentEntity.toDomain() = ArchiveSourceDocument(
    uid = uid,
    versionUid = versionUid,
    fileName = fileName,
    mimeType = mimeType,
    sha256 = sha256,
    storagePath = storagePath,
    type = runCatching { SourceDocumentType.valueOf(documentType) }.getOrDefault(SourceDocumentType.ORIGINAL),
    reviewState = runCatching { ReviewState.valueOf(reviewStatus) }.getOrDefault(ReviewState.PENDING_REVIEW),
    importedAt = importedAt,
    pageCount = pageCount,
    notes = notes
)


/** Preserve coroutine cancellation instead of converting it into an ordinary repository failure. */
private suspend fun <T> safeResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
