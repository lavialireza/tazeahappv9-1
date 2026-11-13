package com.example.bookapp.domain.repository

import com.example.bookapp.domain.model.ArchiveSourceDocument
import com.example.bookapp.domain.model.ArchiveVersion
import com.example.bookapp.domain.model.NewSourceDocument
import com.example.bookapp.domain.model.NewTaziehVersion
import com.example.bookapp.domain.model.ForkTaziehVersion
import com.example.bookapp.domain.model.VersionForkReport
import com.example.bookapp.domain.model.ReviewState
import com.example.bookapp.domain.model.VersionState

/** Domain contract; implementations may use Room, files, or a future remote data source. */
interface ArchiveRepository {
    suspend fun versions(taziehUid: String): List<ArchiveVersion>
    suspend fun createVersion(input: NewTaziehVersion): Result<ArchiveVersion>
    suspend fun forkVersion(input: ForkTaziehVersion): Result<VersionForkReport>
    suspend fun addSource(input: NewSourceDocument): Result<ArchiveSourceDocument>
    suspend fun sources(versionUid: String): List<ArchiveSourceDocument>
    suspend fun updateVersionState(versionUid: String, state: VersionState, changeNote: String = ""): Result<ArchiveVersion>
    suspend fun updateSourceReview(uid: String, state: ReviewState, notes: String = ""): Result<ArchiveSourceDocument>
}
