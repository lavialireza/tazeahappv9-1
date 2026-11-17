package com.example.bookapp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** دیتابیس فقط‌خواندنی دفتر رسمی Viewer؛ DAO این کلاس هیچ عملیات نوشتن ندارد. */
@Database(
    entities = [
        DirectorPlanEntity::class, DirectorPlanVersionEntity::class,
        DirectorSourceFileEntity::class, DirectorTurnEntity::class,
        DirectorParticipantEntity::class, DirectorTextSegmentEntity::class,
        DirectorStageCueEntity::class, DirectorSessionEntity::class,
        DirectorTurnProgressEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OfficialDirectorOfficeDatabase : RoomDatabase() {
    abstract fun officialDao(): OfficialDirectorOfficeDao

    companion object {
        const val FILE_NAME = "director_official.db"
        @Volatile private var instance: OfficialDirectorOfficeDatabase? = null
        fun get(context: Context): OfficialDirectorOfficeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, OfficialDirectorOfficeDatabase::class.java, FILE_NAME
            ).build().also { instance = it }
        }
    }
}

/** دیتابیس مستقل دفتر شخصی Viewer؛ فایل آن با دفتر رسمی مشترک نیست. */
@Database(
    entities = [
        DirectorPlanEntity::class, DirectorPlanVersionEntity::class,
        DirectorSourceFileEntity::class, DirectorTurnEntity::class,
        DirectorParticipantEntity::class, DirectorTextSegmentEntity::class,
        DirectorStageCueEntity::class, DirectorSessionEntity::class,
        DirectorTurnProgressEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PersonalDirectorOfficeDatabase : RoomDatabase() {
    abstract fun personalDao(): DirectorDao

    companion object {
        const val FILE_NAME = "director_personal.db"
        @Volatile private var instance: PersonalDirectorOfficeDatabase? = null
        fun get(context: Context): PersonalDirectorOfficeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, PersonalDirectorOfficeDatabase::class.java, FILE_NAME
            ).build().also { instance = it }
        }
    }
}

/** سطح خواندن دفتر رسمی؛ این DAO عمداً هیچ متد INSERT/UPDATE/DELETE ندارد. */
@Dao
interface OfficialDirectorOfficeDao {
    @Query("SELECT * FROM director_plans WHERE status = 'PUBLISHED' AND isDeleted = 0 ORDER BY updatedAt DESC, id DESC")
    suspend fun publishedPlans(): List<DirectorPlanEntity>

    @Query("SELECT * FROM director_plans WHERE uid = :uid AND status = 'PUBLISHED' AND isDeleted = 0 LIMIT 1")
    suspend fun publishedPlan(uid: String): DirectorPlanEntity?

    @Query("SELECT v.* FROM director_plan_versions v INNER JOIN director_plans p ON p.id = v.planId WHERE p.uid = :planUid AND p.status = 'PUBLISHED' AND p.isDeleted = 0 AND v.status = 'PUBLISHED' ORDER BY v.versionNumber DESC")
    suspend fun publishedVersions(planUid: String): List<DirectorPlanVersionEntity>

    @Query("SELECT * FROM director_turns WHERE versionId = :versionId ORDER BY sequenceIndex, id")
    suspend fun turns(versionId: Long): List<DirectorTurnEntity>

    @Query("SELECT * FROM director_turn_participants WHERE turnId = :turnId ORDER BY orderIndex, id")
    suspend fun participants(turnId: Long): List<DirectorParticipantEntity>

    @Query("SELECT * FROM director_text_segments WHERE turnId = :turnId ORDER BY segmentIndex, id")
    suspend fun textSegments(turnId: Long): List<DirectorTextSegmentEntity>

    @Query("SELECT * FROM director_stage_cues WHERE versionId = :versionId ORDER BY orderIndex, id")
    suspend fun stageCues(versionId: Long): List<DirectorStageCueEntity>
}

/**
 * دروازه عملیات Viewer. مجوزها قبل از هر نوشتن بررسی می‌شوند؛ مخفی‌کردن دکمه به‌تنهایی
 * مجوز محسوب نمی‌شود. انتشار، کپی و خروجی در این API عمداً وجود ندارند.
 */
class ViewerDirectorOfficeStore(context: Context) {
    private val appContext = context.applicationContext
    private val officialDb = OfficialDirectorOfficeDatabase.get(appContext)
    private val personalDb = PersonalDirectorOfficeDatabase.get(appContext)

    private fun requireAccess(key: String) {
        check(ViewerAccessPolicy.hasPermission(appContext, key)) { "دسترسی این قابلیت توسط مدیر فعال نشده است." }
    }

    suspend fun officialPlans(): List<DirectorPlanEntity> {
        requireAccess("director.official.view")
        return officialDb.officialDao().publishedPlans()
    }

    suspend fun officialPlan(uid: String): DirectorPlanEntity? {
        requireAccess("director.official.view")
        return officialDb.officialDao().publishedPlan(uid)
    }

    suspend fun createPersonalPlan(plan: DirectorPlanEntity): Long {
        requireAccess("director.personal.create")
        require(plan.status == DirectorPlanEntity.STATUS_DRAFT) { "دفتر شخصی فقط به‌صورت پیش‌نویس ساخته می‌شود." }
        return personalDb.personalDao().insertPlan(plan.copy(status = DirectorPlanEntity.STATUS_DRAFT))
    }

    suspend fun updatePersonalPlan(plan: DirectorPlanEntity) {
        requireAccess("director.personal.edit")
        val existing = personalDb.personalDao().plan(plan.id)
            ?: error("دفتر شخصی پیدا نشد.")
        require(existing.uid == plan.uid && existing.status != DirectorPlanEntity.STATUS_PUBLISHED) {
            "تغییر دفتر رسمی یا دفتر متعلق به فضای دیگر مجاز نیست."
        }
        personalDb.personalDao().updatePlan(plan.copy(status = DirectorPlanEntity.STATUS_DRAFT))
    }

    suspend fun personalPlans(taziehUid: String): List<DirectorPlanEntity> {
        requireAccess("director.personal.create")
        return personalDb.personalDao().plansForTazieh(taziehUid)
    }

    suspend fun createPersonalVersion(version: DirectorPlanVersionEntity): Long {
        requireAccess("director.personal.edit")
        val plan = personalDb.personalDao().plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        require(plan.status == DirectorPlanEntity.STATUS_DRAFT && version.status == DirectorPlanVersionEntity.STATUS_DRAFT)
        return personalDb.personalDao().insertVersion(version.copy(status = DirectorPlanVersionEntity.STATUS_DRAFT, publishedAt = null))
    }

    suspend fun savePersonalTurn(turn: DirectorTurnEntity) {
        requireAccess("director.personal.edit")
        val version = personalDb.personalDao().version(turn.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = personalDb.personalDao().plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        require(plan.status == DirectorPlanEntity.STATUS_DRAFT && version.status == DirectorPlanVersionEntity.STATUS_DRAFT)
        if (turn.id == 0L) personalDb.personalDao().insertTurn(turn) else personalDb.personalDao().updateTurn(turn)
    }

    suspend fun savePersonalTextSegment(segment: DirectorTextSegmentEntity) {
        requireAccess("director.personal.edit")
        val turn = personalDb.personalDao().turn(segment.turnId) ?: error("نوبت دفتر شخصی پیدا نشد.")
        val version = personalDb.personalDao().version(turn.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = personalDb.personalDao().plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        require(plan.status == DirectorPlanEntity.STATUS_DRAFT && version.status == DirectorPlanVersionEntity.STATUS_DRAFT)
        if (segment.id == 0L) personalDb.personalDao().insertTextSegment(segment) else personalDb.personalDao().updateTextSegment(segment)
    }

    suspend fun savePersonalStageCue(cue: DirectorStageCueEntity) {
        requireAccess("director.personal.edit")
        val version = personalDb.personalDao().version(cue.versionId) ?: error("نسخه دفتر شخصی پیدا نشد.")
        val plan = personalDb.personalDao().plan(version.planId) ?: error("دفتر شخصی پیدا نشد.")
        require(plan.status == DirectorPlanEntity.STATUS_DRAFT && version.status == DirectorPlanVersionEntity.STATUS_DRAFT)
        if (cue.id == 0L) personalDb.personalDao().insertStageCue(cue) else personalDb.personalDao().updateStageCue(cue)
    }

    fun canBuildWith(method: String): Boolean {
        val key = when (method) {
            "ROLE_NUMBERED" -> "director.build.roleNumbered"
            "FULL_TEXT" -> "director.build.fullText"
            "MANUAL" -> "director.build.manual"
            "SELECTOR" -> "director.build.selector"
            else -> return false
        }
        return ViewerAccessPolicy.hasPermission(appContext, key) &&
            ViewerAccessPolicy.hasPermission(appContext, "director.personal.create")
    }
}
