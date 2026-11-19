package com.example.bookapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorOfficeWorkspaceUseCasesTest {
    @Test fun normalizesPersianAndArabicDigits() {
        assertEquals("123", DirectorOfficeWorkspaceUseCases.normalizeNumber("۱۲٣"))
    }

    @Test fun splitsDialoguesAndPreservesInternalLines() {
        assertEquals(
            listOf("بیت اول\nبیت دوم", "دیالوگ دوم"),
            DirectorOfficeWorkspaceUseCases.splitDialogues("بیت اول\nبیت دوم\n\nدیالوگ دوم")
        )
    }

    @Test fun parsesNumberedDialoguesWithPersianDigits() {
        assertEquals(
            listOf(1 to "سلام\nادامه متن", 2 to "پاسخ"),
            DirectorOfficeWorkspaceUseCases.parseNumberedDialogues("۱- سلام\nادامه متن\nدیالوگ ۲: پاسخ")
        )
    }

    @Test fun ignoresUnnumberedTextForNumberedMethod() {
        assertTrue(DirectorOfficeWorkspaceUseCases.parseNumberedDialogues("متن بدون شماره").isEmpty())
    }
}
