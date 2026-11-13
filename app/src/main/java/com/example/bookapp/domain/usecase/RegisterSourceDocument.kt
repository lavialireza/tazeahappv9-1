package com.example.bookapp.domain.usecase

import com.example.bookapp.domain.model.NewSourceDocument
import com.example.bookapp.domain.repository.ArchiveRepository

class RegisterSourceDocument(private val repository: ArchiveRepository) {
    suspend operator fun invoke(input: NewSourceDocument) =
        ArchiveValidation.validateSource(input)?.let { Result.failure(IllegalArgumentException(it)) }
            ?: repository.addSource(input.copy(
                versionUid = input.versionUid.trim(),
                fileName = input.fileName.trim(),
                mimeType = input.mimeType.trim(),
                sha256 = input.sha256.trim().lowercase(),
                storagePath = input.storagePath.trim(),
                notes = input.notes.trim()
            ))
}
