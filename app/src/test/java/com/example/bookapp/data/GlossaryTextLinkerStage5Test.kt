package com.example.bookapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTextLinkerStage5Test {
    private fun term(
        id: Long,
        headword: String,
        status: String = GlossaryTermEntity.STATUS_VERIFIED
    ) = GlossaryTermEntity(
        id = id,
        uid = "term-$id",
        headword = headword,
        normalizedHeadword = GlossaryNormalizer.normalize(headword),
        reviewStatus = status,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun linksApprovedAlternativeSpellingAndKeepsOriginalTextOffsets() {
        val source = "در کربلا، ياران وفادار آمدند."
        val approved = term(1, "یاران")
        val spelling = GlossarySpellingEntity(
            id = 1,
            termId = approved.id,
            spelling = "ياران",
            normalizedSpelling = GlossaryNormalizer.normalize("ياران")
        )

        val linked = GlossaryTextLinker.link(source, listOf(approved), listOf(spelling), emptyList())

        assertEquals(1, linked.size)
        assertEquals("ياران", source.substring(linked.single().start, linked.single().endExclusive))
        assertEquals("term-1", linked.single().termUid)
        assertEquals("در کربلا، ياران وفادار آمدند.", source)
    }

    @Test
    fun doesNotLinkPendingOrRejectedTerms() {
        val pending = term(1, "وفا", GlossaryTermEntity.STATUS_PENDING)
        val rejected = term(2, "یار", GlossaryTermEntity.STATUS_REJECTED)

        val linked = GlossaryTextLinker.link("وفا و یار", listOf(pending, rejected), emptyList(), emptyList())

        assertTrue(linked.isEmpty())
    }

    @Test
    fun popupDataContainsOnlyVerifiedMeaningsAndTheirSources() {
        val approved = term(1, "ذوالفقار")
        val meanings = listOf(
            GlossaryMeaningEntity(
                uid = "meaning-approved", termId = 1, meaning = "نام شمشیر منسوب به امام علی",
                sourceTitle = "فرهنگ تعزیه", reviewStatus = GlossaryTermEntity.STATUS_VERIFIED,
                createdAt = 1L, updatedAt = 1L
            ),
            GlossaryMeaningEntity(
                uid = "meaning-pending", termId = 1, meaning = "معنی بررسی‌نشده",
                reviewStatus = GlossaryTermEntity.STATUS_PENDING,
                createdAt = 1L, updatedAt = 1L
            )
        )

        val linked = GlossaryTextLinker.link("ذوالفقار", listOf(approved), emptyList(), meanings)

        assertEquals(1, linked.size)
        assertEquals(listOf("نام شمشیر منسوب به امام علی"), linked.single().verifiedMeanings)
        assertEquals(listOf("فرهنگ تعزیه"), linked.single().sources)
        assertFalse(linked.single().verifiedMeanings.contains("معنی بررسی‌نشده"))
    }

    @Test
    fun longerHeadwordWinsOverNestedShorterHeadword() {
        val short = term(1, "علی")
        val long = term(2, "امیرالمؤمنین")
        val source = "امیرالمؤمنین"

        val linked = GlossaryTextLinker.link(source, listOf(short, long), emptyList(), emptyList())

        assertEquals(1, linked.size)
        assertEquals("امیرالمؤمنین", source.substring(linked.single().start, linked.single().endExclusive))
        assertEquals("term-2", linked.single().termUid)
    }
}
