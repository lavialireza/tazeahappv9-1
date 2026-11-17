package com.example.bookapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Viewer-only storage for the Director Office.
 *
 * The official publication and the user's private workspace are deliberately
 * two different SQLite files. Importing/replacing official data can therefore
 * never delete or overwrite personal plans.
 *
 * This class exists only in src/viewer and is not compiled into the Admin APK.
 */
@Database(
    entities = [
        DirectorPlanEntity::class,
        DirectorPlanVersionEntity::class,
        DirectorSourceFileEntity::class,
        DirectorTurnEntity::class,
        DirectorParticipantEntity::class,
        DirectorTextSegmentEntity::class,
        DirectorStageCueEntity::class,
        DirectorSessionEntity::class,
        DirectorTurnProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ViewerDirectorOfficeDatabase : RoomDatabase() {
    abstract fun directorDao(): DirectorDao

    companion object {
        private const val OFFICIAL_DB = "director_official.db"
        private const val PERSONAL_DB = "director_personal.db"

        @Volatile private var officialInstance: ViewerDirectorOfficeDatabase? = null
        @Volatile private var personalInstance: ViewerDirectorOfficeDatabase? = null

        fun official(context: Context): ViewerDirectorOfficeDatabase =
            officialInstance ?: synchronized(this) {
                officialInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ViewerDirectorOfficeDatabase::class.java,
                    OFFICIAL_DB
                ).build().also { officialInstance = it }
            }

        fun personal(context: Context): ViewerDirectorOfficeDatabase =
            personalInstance ?: synchronized(this) {
                personalInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ViewerDirectorOfficeDatabase::class.java,
                    PERSONAL_DB
                ).build().also { personalInstance = it }
            }

        /** Test/support hook; never deletes either database file. */
        fun closeInstances() {
            synchronized(this) {
                officialInstance?.close()
                personalInstance?.close()
                officialInstance = null
                personalInstance = null
            }
        }
    }
}
