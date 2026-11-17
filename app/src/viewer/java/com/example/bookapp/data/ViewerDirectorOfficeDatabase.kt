package com.example.bookapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * پایگاه داده مستقل دفتر معین‌البکای Viewer.
 * دو فایل کاملاً مجزا دارد: رسمی فقط برای واردکردن بسته امضاشده Admin و
 * شخصی فقط برای داده‌های قابل ویرایش همین نصب. هیچ‌کدام bookapp.db ادمین نیستند.
 */
@Database(
    entities = [
        DirectorPlanEntity::class,
        DirectorPlanVersionEntity::class,
        DirectorSourceFileEntity::class,
        DirectorTurnEntity::class,
        DirectorParticipantEntity::class,
        DirectorTextSegmentEntity::class,
        DirectorStageCueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ViewerDirectorOfficeDatabase : RoomDatabase() {
    abstract fun directorDao(): ViewerDirectorOfficeDao

    companion object {
        @Volatile private var officialInstance: ViewerDirectorOfficeDatabase? = null
        @Volatile private var personalInstance: ViewerDirectorOfficeDatabase? = null

        fun official(context: Context): ViewerDirectorOfficeDatabase =
            officialInstance ?: synchronized(this) {
                officialInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ViewerDirectorOfficeDatabase::class.java,
                    "director_official.db"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        installOfficialReadOnlyTriggers(db)
                    }
                }).build().also { officialInstance = it }
            }

        fun personal(context: Context): ViewerDirectorOfficeDatabase =
            personalInstance ?: synchronized(this) {
                personalInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ViewerDirectorOfficeDatabase::class.java,
                    "director_personal_${ViewerAccessPolicy.installationId(context).replace(Regex("[^A-Za-z0-9_-]"), "_")}.db"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        installPersonalNoPublishTriggers(db)
                    }
                }).build().also { personalInstance = it }
            }

        internal fun unlockOfficialForVerifiedImport(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            officialTables.forEach { table -> db.execSQL("DROP TRIGGER IF EXISTS `ro_${table}_insert`"); db.execSQL("DROP TRIGGER IF EXISTS `ro_${table}_update`"); db.execSQL("DROP TRIGGER IF EXISTS `ro_${table}_delete`") }
        }

        internal fun lockOfficialAfterImport(db: androidx.sqlite.db.SupportSQLiteDatabase) = installOfficialReadOnlyTriggers(db)

        private val officialTables = listOf(
            "director_plans", "director_plan_versions", "director_source_files", "director_turns",
            "director_turn_participants", "director_text_segments", "director_stage_cues"
        )

        private fun installOfficialReadOnlyTriggers(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            officialTables.forEach { table ->
                db.execSQL("CREATE TRIGGER IF NOT EXISTS `ro_${table}_insert` BEFORE INSERT ON `$table` BEGIN SELECT RAISE(ABORT, 'official director office is read-only'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS `ro_${table}_update` BEFORE UPDATE ON `$table` BEGIN SELECT RAISE(ABORT, 'official director office is read-only'); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS `ro_${table}_delete` BEFORE DELETE ON `$table` BEGIN SELECT RAISE(ABORT, 'official director office is read-only'); END")
            }
        }

        private fun installPersonalNoPublishTriggers(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `personal_plan_no_publish_insert` BEFORE INSERT ON `director_plans` WHEN NEW.status = 'PUBLISHED' BEGIN SELECT RAISE(ABORT, 'personal director office cannot be published'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `personal_plan_no_publish_update` BEFORE UPDATE OF status ON `director_plans` WHEN NEW.status = 'PUBLISHED' BEGIN SELECT RAISE(ABORT, 'personal director office cannot be published'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `personal_version_no_publish_insert` BEFORE INSERT ON `director_plan_versions` WHEN NEW.status = 'PUBLISHED' BEGIN SELECT RAISE(ABORT, 'personal director office cannot be published'); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS `personal_version_no_publish_update` BEFORE UPDATE OF status ON `director_plan_versions` WHEN NEW.status = 'PUBLISHED' BEGIN SELECT RAISE(ABORT, 'personal director office cannot be published'); END")
        }
    }
}
