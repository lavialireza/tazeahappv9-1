package com.example.bookapp.domain.usecase

import com.example.bookapp.domain.model.ReviewState
import com.example.bookapp.domain.model.VersionState
import com.example.bookapp.domain.repository.ArchiveRepository

class ReviewSourceDocument(private val repository: ArchiveRepository) {
    suspend operator fun invoke(uid: String, state: ReviewState, notes: String = "") =
        repository.updateSourceReview(uid.trim(), state, notes.trim())
}

class UpdateArchiveVersionState(private val repository: ArchiveRepository) {
    suspend operator fun invoke(uid: String, state: VersionState, note: String = "") =
        repository.updateVersionState(uid.trim(), state, note.trim())
}
