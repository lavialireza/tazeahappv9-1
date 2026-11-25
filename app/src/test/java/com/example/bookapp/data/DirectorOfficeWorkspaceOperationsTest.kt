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
    fun `same numbered different role text is saved separately with warning`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, versionId = 20, turnNumber = 4, roleUid = "role-a", roleTitle = "نقش اول",
            text = "متن اول", sectionUid = "section-a"
        )
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, versionId = 20, turnNumber = 4, roleUid = "role-b", roleTitle = "نقش دوم",
            text = "متن دوم", sectionUid = "section-b"
        )

        assertEquals(2, dao.turnRows.size)
        assertTrue(dao.turnRows.values.all { it.turnNumber == 4 })
        assertTrue(dao.turnRows.values.any { it.notes.startsWith("هشدار:") })
        assertEquals(setOf("متن اول", "متن دوم"), dao.segmentRows.map { it.text }.toSet())
        assertEquals(setOf("role-a", "role-b"), dao.participantRows.map { it.roleUid }.toSet())
    }

    @Test
    fun `same numbered identical role text becomes one collective turn`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, versionId = 20, turnNumber = 5, roleUid = "role-a", roleTitle = "نقش اول", text = "متن مشترک"
        )
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, versionId = 20, turnNumber = 5, roleUid = "role-b", roleTitle = "نقش دوم", text = "متن مشترک"
        )

        assertEquals(1, dao.turnRows.size)
        assertEquals(DirectorTurnEntity.TYPE_COLLECTIVE, dao.turnRows.values.single().turnType)
        assertEquals(1, dao.segmentRows.size)
        assertEquals(setOf("role-a", "role-b"), dao.participantRows.map { it.roleUid }.toSet())
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
                override suspend fun <T> run(block: suspend () -> T): T {
                    transactionCalled = true
                    return block()
                }
            }
        )

        assertTrue(transactionCalled)
        assertEquals(listOf(2L, 1L), dao.turnRows.values.sortedBy { it.sequenceIndex }.map { it.id })
    }

    @Test
    fun `numbered builder groups same number across roles and preserves source sections`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao = dao,
            versionId = 20,
            method = DirectorOfficeSharedContract.ROLE_NUMBERED,
            roles = listOf(
                DirectorOfficeBuildRole("role-a", "امام حسین", 0),
                DirectorOfficeBuildRole("role-b", "حضرت عباس", 1)
            ),
            sections = listOf(
                DirectorOfficeBuildSection("sec-a", "role-a", "۱- سلام بر سپاه", 0, "بخش اول"),
                DirectorOfficeBuildSection("sec-b", "role-b", "1- سلام بر سپاه", 0, "بخش دوم"),
                DirectorOfficeBuildSection("sec-c", "role-a", "۲- حرکت به میدان", 1, "بخش سوم")
            )
        )

        assertEquals(2, result.insertedTurnCount)
        assertEquals(2, dao.turnRows.size)
        val first = dao.turnRows.values.single { it.turnNumber == 1 }
        assertEquals(DirectorTurnEntity.TYPE_COLLECTIVE, first.turnType)
        assertEquals(setOf("role-a", "role-b"), dao.participantRows.filter { it.turnId == first.id }.map { it.roleUid }.toSet())
        val sharedSegments = dao.segmentRows.filter { it.turnId == first.id }
        assertEquals(1, sharedSegments.size)
        val sourceSegment = sharedSegments.single()
        assertEquals("sec-a\u001Fsec-b", sourceSegment.sourceSectionUids)
        assertEquals("بخش اول، بخش دوم", sourceSegment.sourceSectionTitle)
        assertEquals("سلام بر سپاه", sourceSegment.text)
        assertEquals(null, sourceSegment.sectionUid)
        assertEquals(null, sourceSegment.sourceRoleUid)
        assertEquals("ROLE_NUMBERED", sourceSegment.sourceMethod)
        assertTrue(sourceSegment.sourceContentHash.isNotBlank())
        assertEquals(first.id, result.turnIdsByNumber[1])
    }

    @Test
    fun `different texts with same number are retained as separate turns with persistent warnings`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
            listOf(DirectorOfficeBuildRole("a", "نقش اول"), DirectorOfficeBuildRole("b", "نقش دوم")),
            listOf(
                DirectorOfficeBuildSection("a1", "a", "۱- متن نخست"),
                DirectorOfficeBuildSection("b1", "b", "۱- متن متفاوت")
            )
        )

        assertEquals(2, result.insertedTurnCount)
        assertTrue(result.warnings.any { it.contains("یکسان نیست") })
        assertEquals(2, dao.turnRows.values.count { it.turnNumber == 1 })
        assertEquals(setOf("متن نخست", "متن متفاوت"), dao.segmentRows.map { it.text }.toSet())
        assertTrue(dao.turnRows.values.all { it.turnType == DirectorTurnEntity.TYPE_NORMAL })
        assertTrue(dao.turnRows.values.all { it.notes.startsWith("هشدار:") })
    }

    @Test
    fun `numbered parser supports Persian and Arabic digit headers`() {
        val parsed = DirectorOfficeWorkspaceUseCases.parseNumberedDialogues(
            "دیالوگ شماره ۱: متن اول\nادامه متن\n۲- متن دوم\nنوبت ٣: متن سوم"
        )
        assertEquals(listOf(1, 2, 3), parsed.map { it.first })
        assertEquals("متن اول\nادامه متن", parsed[0].second)
    }

    @Test
    fun `repeated number inside one role is rejected`() = runTest {
        val dao = FakeWorkspaceDao()
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao, 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
                listOf(DirectorOfficeBuildRole("a", "نقش اول")),
                listOf(DirectorOfficeBuildSection("a1", "a", "۱- متن نخست\n۲- متن دوم\n۱- تکرار"))
            )
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("شماره نوبت در یک نقش تکرار شده است"))
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `missing numbered turns are returned as a non blocking warning`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
            listOf(DirectorOfficeBuildRole("a", "نقش اول")),
            listOf(DirectorOfficeBuildSection("a1", "a", "۱- متن اول\n۳- متن سوم"))
        )
        assertEquals(2, result.insertedTurnCount)
        assertTrue(result.warnings.single().contains("۲"))
    }

    @Test
    fun `full text builder creates one turn per role with ordered source sections`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao = dao,
            versionId = 20,
            method = DirectorOfficeSharedContract.FULL_TEXT,
            roles = listOf(DirectorOfficeBuildRole("role-a", "امام حسین", 0), DirectorOfficeBuildRole("role-b", "حضرت عباس", 1)),
            sections = listOf(
                DirectorOfficeBuildSection("sec-a1", "role-a", "بخش اول", 0),
                DirectorOfficeBuildSection("sec-a2", "role-a", "بخش دوم", 1),
                DirectorOfficeBuildSection("sec-b1", "role-b", "بخش سوم", 0)
            )
        )

        assertEquals(2, result.insertedTurnCount)
        assertEquals("امام حسین", dao.turnRows.values.single { it.turnNumber == 1 }.title)
        assertEquals(listOf("بخش اول", "بخش دوم"), dao.segmentRows.filter { it.turnId == result.turnIdsByRole.getValue("role-a") }.sortedBy { it.segmentIndex }.map { it.text })
        assertEquals(2, dao.participantRows.size)
    }

    @Test
    fun `workspace repository loads one flavor independent snapshot with grouped relations`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 4, sequenceIndex = 0, title = "نوبت نخست")
            participantRows += DirectorParticipantEntity(id = 1, turnId = 1, roleUid = "role-1", roleTitle = "امام حسین", isLead = true)
            segmentRows += DirectorTextSegmentEntity(id = 1, turnId = 1, segmentIndex = 0, text = "آغاز دیالوگ", sectionUid = "section-1")
            cueRows += DirectorStageCueEntity(id = 1, versionId = 20, turnId = 1, title = "حرکت", instruction = "ورود به صحنه")
        }

        val state = DaoDirectorOfficeWorkspaceRepository(dao).loadWorkspace(planId = 10, versionId = 20)

        assertEquals(10L, state.plan.id)
        assertEquals(20L, state.selectedVersion?.id)
        assertEquals(1, state.turns.size)
        assertEquals("امام حسین", state.participantsByTurn.getValue(1).single().roleTitle)
        assertEquals("آغاز دیالوگ", state.textSegmentsByTurn.getValue(1).single().text)
        assertEquals("حرکت", state.stageCues.single().title)
        assertTrue(state.searchableTextByTurn.getValue(1).contains("امام حسین"))
        assertTrue(state.searchableTextByTurn.getValue(1).contains("آغاز دیالوگ"))
    }

    @Test
    fun `workspace repository loads all dialogue segments in one bulk query`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "اول")
            turnRows[2] = DirectorTurnEntity(id = 2, versionId = 20, turnNumber = 2, sequenceIndex = 1, title = "دوم")
            segmentRows += DirectorTextSegmentEntity(id = 1, turnId = 1, segmentIndex = 0, text = "متن اول")
            segmentRows += DirectorTextSegmentEntity(id = 2, turnId = 2, segmentIndex = 0, text = "متن دوم")
        }

        val snapshot = DaoDirectorOfficeWorkspaceRepository(dao).loadWorkspace(10, 20)

        assertEquals(1, dao.bulkSegmentQueryCount)
        assertEquals("متن اول", snapshot.textSegmentsByTurn.getValue(1).single().text)
        assertEquals("متن دوم", snapshot.textSegmentsByTurn.getValue(2).single().text)
    }

    @Test
    fun `selector dialogue is created by shared operation and links source section`() = runTest {
        val dao = FakeWorkspaceDao()
        DaoDirectorOfficeWorkspaceRepository(dao).addSelectorDialogue(
            planId = 10,
            entry = DirectorOfficeSelectorEntry(
                key = "section-8:0", roleUid = "role-8", roleTitle = "حضرت زینب",
                sectionUid = "section-8", sectionTitle = "خطاب", text = "متن انتخاب‌شده",
                sourceContentHash = ContentUid.sha256("متن کامل بخش خطاب"),
                sourceTextStartOffset = 4,
                sourceTextEndOffset = 17
            )
        )

        val turn = dao.turnRows.values.single()
        assertEquals(1, turn.turnNumber)
        assertEquals("section-8:0", turn.continuityGroupUid)
        assertEquals("role-8", dao.participantRows.single().roleUid)
        assertEquals("section-8", dao.segmentRows.single().sectionUid)
        assertEquals("متن انتخاب‌شده", dao.segmentRows.single().text)
        assertEquals("role-8", dao.segmentRows.single().sourceRoleUid)
        assertEquals("خطاب", dao.segmentRows.single().sourceSectionTitle)
        assertEquals(ContentUid.sha256("متن کامل بخش خطاب"), dao.segmentRows.single().sourceContentHash)
        assertEquals(4, dao.segmentRows.single().sourcePageStart)
        assertEquals(17, dao.segmentRows.single().sourcePageEnd)
    }

    @Test
    fun `numbered office can be saved empty when the source has no numbered dialogue`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao = dao,
            versionId = 20,
            method = DirectorOfficeSharedContract.ROLE_NUMBERED,
            roles = listOf(DirectorOfficeBuildRole("role-a", "امام حسین", 0)),
            sections = listOf(DirectorOfficeBuildSection("sec-a", "role-a", "متنی بدون شماره نوبت", 0))
        )

        assertEquals(0, result.insertedTurnCount)
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `full text office can be saved empty when no role sections contain text`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao = dao,
            versionId = 20,
            method = DirectorOfficeSharedContract.FULL_TEXT,
            roles = listOf(DirectorOfficeBuildRole("role-a", "امام حسین", 0)),
            sections = emptyList()
        )

        assertEquals(0, result.insertedTurnCount)
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `failed numbered rebuild preserves existing turns when no numbered source is found`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "نوبت موجود")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao = dao,
                versionId = 20,
                method = DirectorOfficeSharedContract.ROLE_NUMBERED,
                roles = listOf(DirectorOfficeBuildRole("role-a", "امام حسین", 0)),
                sections = listOf(DirectorOfficeBuildSection("sec-a", "role-a", "متنی بدون شماره نوبت", 0))
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("نوبت موجود", dao.turnRows.getValue(1).title)
    }

    @Test
    fun `duplicate office titles are assigned a readable ordinal suffix`() {
        assertEquals(
            "دفتر مجلس",
            DirectorOfficeWorkspaceUseCases.uniqueOfficeTitle("دفتر مجلس", emptyList())
        )
        assertEquals(
            "دفتر مجلس (۳)",
            DirectorOfficeWorkspaceUseCases.uniqueOfficeTitle("دفتر مجلس", listOf("دفتر مجلس", "دفتر مجلس (۲)"))
        )
    }

    @Test
    fun `manual builder creates a raw empty draft when no turns exist`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.MANUAL, emptyList(), emptyList()
        )
        assertTrue(dao.turnRows.isEmpty())
        assertEquals(0, result.insertedTurnCount)
    }

    @Test
    fun `manual builder does not delete already recorded turns`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 5, sequenceIndex = 0, title = "حفظ شود")
        }
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.MANUAL, emptyList(), emptyList()
        )
        assertEquals(1, dao.turnRows.size)
        assertEquals(0, result.insertedTurnCount)
    }

    @Test
    fun `selector builder clears current turns but does not create turns`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 5, sequenceIndex = 0, title = "پاک شود")
        }
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.SELECTOR,
            listOf(DirectorOfficeBuildRole("role-a", "نقش", 0)),
            listOf(DirectorOfficeBuildSection("sec-a", "role-a", "متن", 0))
        )
        assertTrue(dao.turnRows.isEmpty())
        assertEquals(0, result.insertedTurnCount)
    }

    @Test
    fun `archived office cannot be edited through shared operations`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            planRow = planRow.copy(status = DirectorPlanEntity.STATUS_ARCHIVED)
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "اول")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.updateTurn(dao, dao.turnRows.getValue(1).copy(turnNumber = 2))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(1, dao.turnRows.getValue(1).turnNumber)
    }

    @Test
    fun `legacy published parent can still receive edits in a draft version`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            planRow = planRow.copy(status = DirectorPlanEntity.STATUS_PUBLISHED)
        }
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao = dao, versionId = 20, turnNumber = 1, roleUid = "role-2",
            roleTitle = "امام حسین", text = "متن نسخه جدید"
        )
        assertEquals(1, dao.turnRows.size)
    }

    @Test
    fun `published version remains immutable even under a legacy published parent`() = runTest {
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

        assertTrue(error != null)
        assertTrue(dao.cueRows.isEmpty())
    }

    @Test
    fun `dialogue edit is recorded and previous text can be restored`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "امام حسین")
            segmentRows += DirectorTextSegmentEntity(id = 1, turnId = 1, segmentIndex = 0, text = "متن اولیه", sectionUid = "section-1")
        }
        val repository = DaoDirectorOfficeWorkspaceRepository(dao)

        repository.updateTextSegment(dao.segmentRows.single().copy(text = "متن اصلاح‌شده"))
        val edit = dao.historyRows.single()
        assertEquals("DIALOGUE", edit.entityType)
        assertEquals("متن اصلاح‌شده", dao.segmentRows.single().text)

        repository.restoreChangeHistory(edit.id)
        assertEquals("متن اولیه", dao.segmentRows.single().text)
        assertEquals("role-a", dao.segmentRows.single().sourceRoleUid)
        assertEquals("section-1", dao.segmentRows.single().sourceSectionUids)
        assertEquals("RESTORE", dao.historyRows.last().changeType)
        assertEquals(edit.id, dao.historyRows.last().restoredFromId)
    }

    @Test
    fun `role changes are recorded and previous participants can be restored`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 1, sequenceIndex = 0, title = "نوبت")
            participantRows += DirectorParticipantEntity(id = 1, turnId = 1, roleUid = "role-a", roleTitle = "نقش اول", isLead = true)
        }
        val repository = DaoDirectorOfficeWorkspaceRepository(dao)

        repository.updateParticipant(dao.participantRows.single().copy(roleUid = "role-b", roleTitle = "نقش دوم"))
        val edit = dao.historyRows.single()
        assertEquals("ROLE", edit.entityType)
        assertEquals("role-b", dao.participantRows.single().roleUid)

        repository.restoreChangeHistory(edit.id)
        assertEquals("role-a", dao.participantRows.single().roleUid)
        assertEquals("نقش اول", dao.participantRows.single().roleTitle)
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
        val historyRows = mutableListOf<DirectorChangeHistoryEntity>()
        private var nextHistoryId = 1L
        private var nextTurnId = 100L
        var bulkSegmentQueryCount = 0
            private set

        override suspend fun plan(id: Long) = planRow.takeIf { it.id == id }
        override suspend fun versionsForPlan(planId: Long) = listOf(versionRow).filter { it.planId == planId }
        override suspend fun softDeletePlan(id: Long, deletedAt: Long) { if (planRow.id == id) planRow = planRow.copy(isDeleted = true, deletedAt = deletedAt, updatedAt = deletedAt) }
        override suspend fun insertParticipant(value: DirectorParticipantEntity): Long {
            participantRows += value.copy(id = participantRows.size + 1L)
            return participantRows.last().id
        }
        override suspend fun updateParticipant(value: DirectorParticipantEntity) {
            val index = participantRows.indexOfFirst { it.id == value.id }
            if (index >= 0) participantRows[index] = value
        }
        override suspend fun deleteParticipant(id: Long) { participantRows.removeAll { it.id == id } }
        override suspend fun deleteTurn(id: Long) {
            turnRows.remove(id)
            participantRows.removeAll { it.turnId == id }
            segmentRows.removeAll { it.turnId == id }
        }
        override suspend fun deleteTextSegment(id: Long) { segmentRows.removeAll { it.id == id } }
        override suspend fun stageCue(id: Long) = cueRows.firstOrNull { it.id == id }
        override suspend fun deleteStageCue(id: Long) { cueRows.removeAll { it.id == id } }
        override suspend fun stageCues(versionId: Long) = cueRows.filter { it.versionId == versionId }
        override suspend fun version(id: Long) = versionRow.takeIf { it.id == id }
        override suspend fun turn(id: Long) = turnRows[id]
        override suspend fun participants(turnId: Long) = participantRows.filter { it.turnId == turnId }
        override suspend fun participantsForTurns(turnIds: List<Long>) = participantRows.filter { it.turnId in turnIds }
        override suspend fun textSegments(turnId: Long) = segmentRows.filter { it.turnId == turnId }
        override suspend fun textSegmentsForTurns(turnIds: List<Long>): List<DirectorTextSegmentEntity> {
            bulkSegmentQueryCount++
            return segmentRows.filter { it.turnId in turnIds }.sortedWith(compareBy<DirectorTextSegmentEntity> { it.turnId }.thenBy { it.segmentIndex }.thenBy { it.id })
        }
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
        override suspend fun insertChangeHistory(value: DirectorChangeHistoryEntity): Long {
            val id = nextHistoryId++
            historyRows += value.copy(id = id)
            return id
        }
        override suspend fun changeHistory(planId: Long, turnId: Long?): List<DirectorChangeHistoryEntity> =
            historyRows.filter { it.planId == planId && (turnId == null || it.turnId == turnId) }
                .sortedByDescending { it.changedAt }
        override suspend fun changeHistoryEntry(id: Long): DirectorChangeHistoryEntity? = historyRows.firstOrNull { it.id == id }
    }

    @Test
    fun `blank numbered dialogue is rejected before existing turns are touched`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 8, sequenceIndex = 0, title = "محفوظ")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao, 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
                listOf(DirectorOfficeBuildRole("role-a", "امام حسین")),
                listOf(DirectorOfficeBuildSection("section-a", "role-a", "۱- متن اول\\n۲-\\n۳- متن سوم"))
            )
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("متن وارد نشده"))
        assertEquals("محفوظ", dao.turnRows.getValue(1).title)
    }

    @Test
    fun `collective dialogue text compares after whitespace normalization and stores once`() = runTest {
        val dao = FakeWorkspaceDao()
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            dao, 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
            listOf(DirectorOfficeBuildRole("a", "نقش اول"), DirectorOfficeBuildRole("b", "نقش دوم")),
            listOf(
                DirectorOfficeBuildSection("a1", "a", "۱- سلام\\nبر شما"),
                DirectorOfficeBuildSection("b1", "b", "۱- سلام   بر شما")
            )
        )
        assertEquals(1, result.insertedTurnCount)
        assertEquals(2, dao.participantRows.size)
        assertEquals(1, dao.segmentRows.size)
        assertEquals("سلام\\nبر شما", dao.segmentRows.single().text)
    }

    @Test
    fun `duplicate source section uid is rejected before any turn is written`() = runTest {
        val dao = FakeWorkspaceDao()
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao, 20, DirectorOfficeSharedContract.FULL_TEXT,
                listOf(DirectorOfficeBuildRole("a", "نقش اول")),
                listOf(
                    DirectorOfficeBuildSection("same", "a", "متن اول"),
                    DirectorOfficeBuildSection("same", "a", "متن دوم")
                )
            )
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("بیش از یک بار"))
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `manual role linked turn cannot create a second row with an existing number`() = runTest {
        val dao = FakeWorkspaceDao().apply {
            turnRows[1] = DirectorTurnEntity(id = 1, versionId = 20, turnNumber = 4, sequenceIndex = 0, title = "نوبت اول")
        }
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
                dao, 20, 4, "role-b", "نقش دوم", "متن دوم"
            )
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("متن نقش جدید با متن نوبت"))
        assertEquals(1, dao.turnRows.size)
    }

    @Test
    fun `numbered builder warns when the first source turn starts after one`() = runTest {
        val result = DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
            FakeWorkspaceDao(), 20, DirectorOfficeSharedContract.ROLE_NUMBERED,
            listOf(DirectorOfficeBuildRole("role-a", "نقش اول")),
            listOf(DirectorOfficeBuildSection("section-a", "role-a", "۳- متن سوم"))
        )
        assertTrue(result.warnings.single().contains("۱، ۲"))
    }

    @Test
    fun `blank selected source section is rejected as incomplete input`() = runTest {
        val dao = FakeWorkspaceDao()
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.buildDraftFromLibrary(
                dao, 20, DirectorOfficeSharedContract.FULL_TEXT,
                listOf(DirectorOfficeBuildRole("role-a", "نقش اول")),
                listOf(DirectorOfficeBuildSection("section-a", "role-a", "   "))
            )
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("متن ندارد"))
        assertTrue(dao.turnRows.isEmpty())
    }

    @Test
    fun `same numbered linked role with identical text joins one collective turn`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, 20, 5, "role-a", "نقش اول", "متن مشترک"
        )
        DirectorOfficeWorkspaceOperations.addRoleLinkedTurn(
            dao, 20, 5, "role-b", "نقش دوم", "متن   مشترک"
        )
        assertEquals(1, dao.turnRows.size)
        assertEquals(2, dao.participantRows.size)
        assertEquals(1, dao.segmentRows.size)
        assertEquals(DirectorTurnEntity.TYPE_COLLECTIVE, dao.turnRows.values.single().turnType)
    }

    @Test
    fun `same numbered selector role joins only when the shared text matches`() = runTest {
        val dao = FakeWorkspaceDao()
        val first = DirectorOfficeSelectorEntry(
            "key-a", "role-a", "نقش اول", "section-a", "بخش اول", "متن مشترک"
        )
        val second = DirectorOfficeSelectorEntry(
            "key-b", "role-b", "نقش دوم", "section-b", "بخش دوم", "متن مشترک"
        )
        DirectorOfficeWorkspaceOperations.addSelectorDialogue(dao, 10, first, 6, 20)
        DirectorOfficeWorkspaceOperations.addSelectorDialogue(dao, 10, second, 6, 20)
        assertEquals(1, dao.turnRows.size)
        assertEquals(2, dao.participantRows.size)
        assertEquals(1, dao.segmentRows.size)
        assertEquals(DirectorTurnEntity.TYPE_COLLECTIVE, dao.turnRows.values.single().turnType)
    }

    @Test
    fun `manual duplicate number with different text is rejected without mutation`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addTurn(dao, 10, 7, "نقش اول", "متن اول")
        val error = runCatching {
            DirectorOfficeWorkspaceOperations.addTurn(dao, 10, 7, "نقش دوم", "متن متفاوت")
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("متن نقش جدید با متن نوبت"))
        assertEquals(1, dao.turnRows.size)
        assertEquals(1, dao.participantRows.size)
    }

    @Test
    fun `manual same number and same text becomes one collective turn`() = runTest {
        val dao = FakeWorkspaceDao()
        DirectorOfficeWorkspaceOperations.addTurn(dao, 10, 9, "نقش اول", "متن مشترک")
        DirectorOfficeWorkspaceOperations.addTurn(dao, 10, 9, "نقش دوم", "متن   مشترک")
        assertEquals(1, dao.turnRows.size)
        assertEquals(2, dao.participantRows.size)
        assertEquals(1, dao.segmentRows.size)
        assertEquals(DirectorTurnEntity.TYPE_COLLECTIVE, dao.turnRows.values.single().turnType)
    }
}
