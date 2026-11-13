package com.example.bookapp.domain.usecase

import com.example.bookapp.domain.model.NewTaziehVersion
import com.example.bookapp.domain.repository.ArchiveRepository

class CreateTaziehVersion(private val repository: ArchiveRepository) {
    suspend operator fun invoke(input: NewTaziehVersion) =
        ArchiveValidation.validateVersion(input)?.let { Result.failure(IllegalArgumentException(it)) }
            ?: repository.createVersion(input.copy(
                taziehUid = input.taziehUid.trim(),
                title = input.title.trim(),
                changeNote = input.changeNote.trim()
            ))
}
