package com.example.bookapp.domain.usecase

import com.example.bookapp.domain.model.NewSourceDocument
import com.example.bookapp.domain.model.NewTaziehVersion
import com.example.bookapp.domain.model.ForkTaziehVersion
import com.example.bookapp.domain.model.VersionState

/** Shared validation rules, kept in the domain layer for deterministic unit testing. */
object ArchiveValidation {
    fun validateVersion(input: NewTaziehVersion): String? = when {
        input.taziehUid.isBlank() -> "شناسه تعزیه مشخص نشده است."
        input.title.isBlank() -> "عنوان نسخه الزامی است."
        else -> null
    }

    fun validateFork(input: ForkTaziehVersion): String? = when {
        input.sourceVersionUid.isBlank() -> "نسخه مبدأ مشخص نشده است."
        input.title.isBlank() -> "عنوان نسخه جدید الزامی است."
        input.title.length > 200 -> "عنوان نسخه نباید بیشتر از ۲۰۰ نویسه باشد."
        input.changeNote.length > 2000 -> "شرح تغییرات نباید بیشتر از ۲۰۰۰ نویسه باشد."
        else -> null
    }

    fun validateSource(input: NewSourceDocument): String? = when {
        input.versionUid.isBlank() -> "نسخه تعزیه مشخص نشده است."
        input.fileName.isBlank() -> "نام فایل الزامی است."
        input.storagePath.isBlank() -> "مسیر نگهداری سند الزامی است."
        !input.sha256.matches(Regex("^[a-fA-F0-9]{64}$")) -> "هش SHA-256 معتبر نیست."
        input.pageCount != null && input.pageCount < 1 -> "تعداد صفحه باید حداقل یک باشد."
        else -> null
    }

    /** Published versions are immutable; publication requires an explicit reviewed state. */
    fun isAllowedVersionTransition(from: VersionState, to: VersionState): Boolean = when (from) {
        VersionState.LEGACY_UNREVIEWED -> to == VersionState.LEGACY_UNREVIEWED || to == VersionState.DRAFT || to == VersionState.REVIEWED
        VersionState.DRAFT -> to == VersionState.DRAFT || to == VersionState.REVIEWED
        VersionState.REVIEWED -> to == VersionState.REVIEWED || to == VersionState.DRAFT || to == VersionState.PUBLISHED
        VersionState.PUBLISHED -> to == VersionState.PUBLISHED
    }
}
