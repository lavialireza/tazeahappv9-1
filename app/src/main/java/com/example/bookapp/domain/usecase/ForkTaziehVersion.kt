package com.example.bookapp.domain.usecase

import com.example.bookapp.domain.model.ForkTaziehVersion
import com.example.bookapp.domain.repository.ArchiveRepository

/** Creates an independent child revision from an existing archive revision. */
class ForkTaziehVersionUseCase(private val repository: ArchiveRepository) {
    suspend operator fun invoke(input: ForkTaziehVersion) =
        ArchiveValidation.validateFork(input)?.let { Result.failure(IllegalArgumentException(it)) }
            ?: repository.forkVersion(input.copy(
                sourceVersionUid = input.sourceVersionUid.trim(),
                title = input.title.trim(),
                changeNote = input.changeNote.trim()
            ))
}
