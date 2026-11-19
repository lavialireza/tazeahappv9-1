package com.example.bookapp.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorOfficeWorkspaceOperationsTest {
    @Test
    fun `role linked turn creation uses shared operation and keeps section link`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao = dao,
            versionId = 20,
            turnNumber = 7,
            roleUid = "role-1",
            roleTitle = "حضرت عباس",
            text = "متن نوبت",
            sectionUid = "section-4",
            transactionRunner = DirectDirectorOfficeTransactionRunner
        )

        val turn = dao.turnRows.values.single()
        assertEquals(7, turn.turnNumber)
        assertEquals("حضرت عباس", turn.title)
        assertEquals("role-1", dao.participantRows.single().roleUid)
        assertEquals("section-4", dao.segmentRows.single().sectionUid)
        assertEquals("متن نوبت", dao.segmentRows.single().text)
    }

    @Test
    fun `reordering uses shared transaction boundary and normalized sequence`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "اول")
            turnRows[2] = DirectorTurnEntity(id = 2, versionId = 20, turnNumber = 2, sequenceIndex = 1, title = "دوم")
        }
        var transactionCalled = false

        DirectorOfficeWorkspaceOperations.reorderTurns(
            dao,
            listOf(dao.turnRows.getValue(2), dao.turnRows.getValue(1)),
            transactionRunner = object : DirectorOfficeTransactionRunner {
                override suspend fun run(block: suspend () -> Unit) {
                    transactionCalled = true
                    block()
                }
            }
        )

        assertTrue(transactionCalled)
        assertEquals(listOf(2L, 1L), dao.turnRows.values.sortedBy { it.sequenceIndex }.map { it.id })
    }

    @Test
    fun `published office cannot be edited through shared operations`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            planRow = planRow.copy(status = DirectorPlanEntity.STATUS_PUBLISHED)
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "اول")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.updateTurn(dao, dao.turnRows.getValue(1).copy(turnNumber = 2))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(1, dao.turnRows.getValue(1).turnNumber)
    }

    @Test
    fun `admin may edit a draft version while the parent plan has a published version`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            planRow = planRow.copy(status = DirectorPlanEntity.STATUS_PUBLISHED)
        }
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao = dao,
            versionId = 20,
            turnNumber = 1,
            roleUid = "role-2",
            roleTitle = "امام حسین",
            text = "متن نسخه جدید",
            allowPublishedPlan = true,
            transactionRunner = DirectDirectorOfficeTransactionRunner
        )
        assertEquals(1, dao.turnRows.size)
    }

    @Test
    fun `published version stays immutable even when admin allows published parent plan`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            versionRow = versionRow.copy(status = DirectorPlanVersionEntity.STATUS_PUBLISHED)
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
                dao = dao,
                versionId = 20,
                turnNumber = 1,
                roleUid = "role-2",
                roleTitle = "امام حسین",
                text = "متن",
                allowPublishedPlan = true,
                transactionRunner = DirectDirectorOfficeTransactionRunner
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `stage cue cannot point to a turn in another version`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[99] = DirectorTurnEntity(id = 99, versionId = 999, turnNumber = 1, sequenceIndex = 0, title = "نسخه دیگر")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.addStageCue(dao, 20, "راهنما", "شرح", 99)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(dao.cueRows.isEmpty())
    }

    private class FakeWorkspaceDao : DirectorOfficeWorkspaceDao {
        var planRow = DirectorPlanEntity(
            id = 10, uid = "plan-10", taziehUid = "tazieh-1", title = "دفتر آزمایشی",
            status = DirectorPlanEntity.STATUS_DRAFT, createdAt = 1, updatedAt = 1
        )
        var versionRow = DirectorPlanVersionEntity(
            id = 20, planId = 10, versionNumber = 1, title = "نسخه پایه",
            status = DirectorPlanVersionEntity.STATUS_DRAFT, createdAt = 1
        )
        val turnRows = linkedMapOf<Long, DirectorTurnEntity>()
        val participantRows = mutableListOf<DirectorParticipantEntity>()
        val segmentRows = mutableListOf<DirectorTextSegmentEntity>()
        val cueRows = mutableListOf<DirectorStageCueEntity>()
        private var nextTurnId = 100L

        override suspend fun plan(id: Long) = planRow.takeIf { it.id == id }
        override suspend fun versions(planId: Long) = listOf(versionRow).filter { it.planId == planId }
        override suspend fun stageCues(versionId: Long) = cueRows.filter { it.versionId == versionId }
        override suspend fun version(id: Long) = versionRow.takeIf { it.id == id }
        override suspend fun turn(id: Long) = turnRows[id]
        override suspend fun participants(turnId: Long) = participantRows.filter { it.turnId == turnId }
        override suspend fun participantsForTurns(turnIds: List<Long>) = participantRows.filter { it.turnId in turnIds }
        override suspend fun textSegments(turnId: Long) = segmentRows.filter { it.turnId == turnId }
        override suspend fun turns(versionId: Long) = turnRows.values.filter { it.versionId == versionId }.sortedBy { it.sequenceIndex }
        override suspend fun updatePlan(value: DirectorPlanEntity) { planRow = value }
        override suspend fun insertTurn(value: DirectorTurnEntity): Long {
            val id = if (value.id != 0L) value.id else nextTurnId++
            turnRows[id] = value.copy(id = id)
            return id
        }
        override suspend fun updateTurn(value: DirectorTurnEntity) { turnRows[value.id] = value }
        override suspend fun moveTurn(turnId: Long, sequenceIndex: Int) {
            turnRows[turnId]?.let { turnRows[turnId] = it.copy(sequenceIndex = sequenceIndex) }
        }
        override suspend fun insertParticipant(value: DirectorParticipantEntity): Long {
            participantRows += value.copy(id = participantRows.size + 1L)
            return participantRows.last().id
        }
        override suspend fun insertTextSegment(value: DirectorTextSegmentEntity): Long {
            segmentRows += value.copy(id = segmentRows.size + 1L)
            return segmentRows.last().id
        }
        override suspend fun updateTextSegment(value: DirectorTextSegmentEntity) {
            val index = segmentRows.indexOfFirst { it.id == value.id }
            if (index >= 0) segmentRows[index] = value
        }
        override suspend fun insertStageCue(value: DirectorStageCueEntity): Long {
            cueRows += value.copy(id = cueRows.size + 1L)
            return cueRows.last().id
        }
        override suspend fun updateStageCue(value: DirectorStageCueEntity) {
            val index = cueRows.indexOfFirst { it.id == value.id }
            if (index >= 0) cueRows[index] = value
        }
    }
}
