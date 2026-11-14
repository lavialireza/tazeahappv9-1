package com.example.bookapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

const val APP_DATABASE_SCHEMA_VERSION = 19

@Database(
    entities = [
        FieldEntity::class,
        TaziehEntity::class,
        RoleEntity::class,
        SectionEntity::class,
        NoteEntity::class,
        FootnoteEntity::class,
        DialogueEntity::class,
        DialogueTurnEntity::class,

        // Multi-role dialogue and rehearsal entities
        DialogueRoleSelectionEntity::class,
        DialogueTurnMetadataEntity::class,
        DialogueStageCueEntity::class,

        TaziehImageEntity::class,
        BookmarkEntity::class,
        SectionTagEntity::class,
        RecentSectionEntity::class,
        ReadingHistoryEntity::class,
        MyRoleEntity::class,
        ActiveDayEntity::class,
        com.example.bookapp.data.archive.TaziehVersionEntity::class,
        com.example.bookapp.data.archive.SourceDocumentEntity::class,
        GlossaryTermEntity::class,
        GlossarySpellingEntity::class,
        GlossaryMeaningEntity::class,
        GlossaryHistoryEntity::class,
        EncyclopediaRecordEntity::class,
        EncyclopediaLinkEntity::class,
        EncyclopediaHistoryEntity::class,
        PerformanceGroupEntity::class,
        GroupMemberEntity::class,
        PerformanceEntity::class,
        PerformanceAssignmentEntity::class,
        PerformanceNoteEntity::class,
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
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun fieldDao(): FieldDao
    abstract fun taziehDao(): TaziehDao
    abstract fun roleDao(): RoleDao
    abstract fun sectionDao(): SectionDao
    abstract fun searchDao(): SearchDao
    abstract fun noteDao(): NoteDao
    abstract fun footnoteDao(): FootnoteDao
    abstract fun dialogueDao(): DialogueDao
    abstract fun dialogueTurnDao(): DialogueTurnDao
    abstract fun dialogueRoleSelectionDao(): DialogueRoleSelectionDao
    abstract fun dialogueTurnMetadataDao(): DialogueTurnMetadataDao
    abstract fun dialogueStageCueDao(): DialogueStageCueDao
    abstract fun taziehImageDao(): TaziehImageDao
    abstract fun userDataDao(): UserDataDao
    abstract fun archiveDao(): com.example.bookapp.data.archive.ArchiveDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun encyclopediaDao(): EncyclopediaDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun directorDao(): DirectorDao

    companion object {

        /** Version 19: soft-delete director plans without deleting source files or text. */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `director_plans` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `director_plans` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plans_isDeleted` ON `director_plans` (`isDeleted`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogues` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `taziehId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        FOREIGN KEY(`taziehId`) REFERENCES `taziehs`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dialogues_taziehId` ON `dialogues` (`taziehId`)"
                )

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogue_turns` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `dialogueId` INTEGER NOT NULL,
                        `sectionId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`dialogueId`) REFERENCES `dialogues`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`sectionId`) REFERENCES `sections`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dialogue_turns_dialogueId` ON `dialogue_turns` (`dialogueId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_dialogue_turns_sectionId` ON `dialogue_turns` (`sectionId`)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tazieh_images` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `taziehId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `caption` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`taziehId`) REFERENCES `taziehs`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tazieh_images_taziehId` ON `tazieh_images` (`taziehId`)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `taziehs` ADD COLUMN `author` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `taziehs` ADD COLUMN `authorEmail` TEXT DEFAULT NULL")
            }
        }

        /** Version 9: add portable UIDs and content-source tracking without destroying user data. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val additions = listOf(
                    "ALTER TABLE `fields` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `taziehs` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `roles` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `sections` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `sections` ADD COLUMN `sourceUid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `notes` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `footnotes` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `dialogues` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `dialogue_turns` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE `tazieh_images` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''"
                )

                additions.forEach(db::execSQL)

                db.execSQL("UPDATE fields SET uid = 'legacy-field-' || id WHERE uid = ''")
                db.execSQL("UPDATE taziehs SET uid = 'legacy-tazieh-' || id WHERE uid = ''")
                db.execSQL("UPDATE roles SET uid = 'legacy-role-' || id WHERE uid = ''")
                db.execSQL("UPDATE sections SET uid = 'legacy-section-' || id WHERE uid = ''")
                db.execSQL("UPDATE notes SET uid = 'legacy-note-' || id WHERE uid = ''")
                db.execSQL("UPDATE footnotes SET uid = 'legacy-footnote-' || id WHERE uid = ''")
                db.execSQL("UPDATE dialogues SET uid = 'legacy-dialogue-' || id WHERE uid = ''")
                db.execSQL("UPDATE dialogue_turns SET uid = 'legacy-dialogue-turn-' || id WHERE uid = ''")
                db.execSQL("UPDATE tazieh_images SET uid = 'legacy-image-' || id WHERE uid = ''")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fields_uid` ON `fields` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_taziehs_uid` ON `taziehs` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_roles_uid` ON `roles` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sections_uid` ON `sections` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sections_sourceUid` ON `sections` (`sourceUid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_uid` ON `notes` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_footnotes_uid` ON `footnotes` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dialogues_uid` ON `dialogues` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dialogue_turns_uid` ON `dialogue_turns` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tazieh_images_uid` ON `tazieh_images` (`uid`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `sectionUid` TEXT DEFAULT NULL")

                db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `sectionUid` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_sectionUid` ON `bookmarks` (`sectionUid`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `section_tags` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `sectionUid` TEXT NOT NULL, `tag` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_section_tags_sectionUid` ON `section_tags` (`sectionUid`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `recent_sections` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `sectionUid` TEXT NOT NULL, `position` INTEGER NOT NULL, `visitedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recent_sections_sectionUid` ON `recent_sections` (`sectionUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_sections_position` ON `recent_sections` (`position`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `reading_history` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `sectionUid` TEXT NOT NULL, `firstReadAt` INTEGER NOT NULL, `lastReadAt` INTEGER NOT NULL, `readCount` INTEGER NOT NULL, `totalSeconds` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_history_sectionUid` ON `reading_history` (`sectionUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_history_lastReadAt` ON `reading_history` (`lastReadAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `my_roles` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `taziehUid` TEXT NOT NULL, `roleUid` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_my_roles_taziehUid` ON `my_roles` (`taziehUid`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `active_days` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `dayKey` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_active_days_dayKey` ON `active_days` (`dayKey`)")
            }
        }

        /** Version 11: introduce versioned archive and source-document metadata without rewriting legacy text. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `roles` ADD COLUMN `versionUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_roles_versionUid` ON `roles` (`versionUid`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tazieh_versions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `taziehUid` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isCurrent` INTEGER NOT NULL,
                        `changeNote` TEXT NOT NULL,
                        FOREIGN KEY(`taziehUid`) REFERENCES `taziehs`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tazieh_versions_taziehUid` ON `tazieh_versions` (`taziehUid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tazieh_versions_taziehUid_versionNumber` ON `tazieh_versions` (`taziehUid`, `versionNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tazieh_versions_uid` ON `tazieh_versions` (`uid`)")

                db.execSQL("""
                    INSERT INTO `tazieh_versions`
                    (`uid`, `taziehUid`, `versionNumber`, `title`, `status`, `createdAt`, `updatedAt`, `isCurrent`, `changeNote`)
                    SELECT 'legacy-version-' || `uid`, `uid`, 1, 'نسخه اولیه موجود', 'LEGACY_UNREVIEWED',
                           CAST(strftime('%s','now') AS INTEGER) * 1000,
                           CAST(strftime('%s','now') AS INTEGER) * 1000, 1,
                           'نسخه ایجادشده از داده‌های نصب قبلی؛ نیازمند تطبیق با سند اصلی'
                    FROM `taziehs`
                """.trimIndent())

                db.execSQL("""
                    UPDATE `roles`
                    SET `versionUid` = (
                        SELECT 'legacy-version-' || `taziehs`.`uid`
                        FROM `taziehs` WHERE `taziehs`.`id` = `roles`.`taziehId`
                    )
                    WHERE EXISTS (SELECT 1 FROM `taziehs` WHERE `taziehs`.`id` = `roles`.`taziehId`)
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `source_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `versionUid` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `storagePath` TEXT NOT NULL,
                        `documentType` TEXT NOT NULL,
                        `reviewStatus` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL,
                        `pageCount` INTEGER,
                        `notes` TEXT NOT NULL,
                        FOREIGN KEY(`versionUid`) REFERENCES `tazieh_versions`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_documents_versionUid` ON `source_documents` (`versionUid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_source_documents_uid` ON `source_documents` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_documents_sha256` ON `source_documents` (`sha256`)")
            }
        }

        /** Version 12: record explicit revision lineage for safely cloned archive versions. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tazieh_versions` ADD COLUMN `parentVersionUid` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tazieh_versions_parentVersionUid` ON `tazieh_versions` (`parentVersionUid`)")
                db.execSQL("ALTER TABLE `dialogues` ADD COLUMN `versionUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialogues_versionUid` ON `dialogues` (`versionUid`)")

                db.execSQL("""
                    UPDATE `dialogues`
                    SET `versionUid` = COALESCE((
                        SELECT `roles`.`versionUid`
                        FROM `dialogue_turns`
                        INNER JOIN `sections` ON `sections`.`id` = `dialogue_turns`.`sectionId`
                        INNER JOIN `roles` ON `roles`.`id` = `sections`.`roleId`
                        WHERE `dialogue_turns`.`dialogueId` = `dialogues`.`id`
                        ORDER BY `dialogue_turns`.`id` LIMIT 1
                    ), '')
                    WHERE `versionUid` = ''
                """.trimIndent())
            }
        }

        /** Version 13: research glossary with multiple meanings, spelling variants, citations and audit history. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `glossary_terms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `headword` TEXT NOT NULL, `normalizedHeadword` TEXT NOT NULL, `partOfSpeech` TEXT NOT NULL, `pronunciation` TEXT NOT NULL, `etymology` TEXT NOT NULL, `notes` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_glossary_terms_normalizedHeadword` ON `glossary_terms` (`normalizedHeadword`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_terms_reviewStatus` ON `glossary_terms` (`reviewStatus`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `glossary_spellings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `termId` INTEGER NOT NULL, `spelling` TEXT NOT NULL, `normalizedSpelling` TEXT NOT NULL, `spellingType` TEXT NOT NULL, FOREIGN KEY(`termId`) REFERENCES `glossary_terms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_spellings_termId` ON `glossary_spellings` (`termId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_glossary_spellings_normalizedSpelling` ON `glossary_spellings` (`normalizedSpelling`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `glossary_meanings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `termId` INTEGER NOT NULL, `meaning` TEXT NOT NULL, `sourceTitle` TEXT NOT NULL, `sourceAuthor` TEXT NOT NULL, `sourcePage` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `usageTaziehUid` TEXT, `usageVersionUid` TEXT, `usageRoleUid` TEXT, `usageSectionUid` TEXT, `usageQuote` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`termId`) REFERENCES `glossary_terms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_meanings_termId` ON `glossary_meanings` (`termId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_meanings_reviewStatus` ON `glossary_meanings` (`reviewStatus`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `glossary_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `termId` INTEGER NOT NULL, `action` TEXT NOT NULL, `beforeJson` TEXT NOT NULL, `afterJson` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, `actor` TEXT NOT NULL, FOREIGN KEY(`termId`) REFERENCES `glossary_terms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_history_termId` ON `glossary_history` (`termId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glossary_history_changedAt` ON `glossary_history` (`changedAt`)")
            }
        }

        /** Version 14: reviewable encyclopedia entities and stable links to archive text. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `encyclopedia_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `normalizedTitle` TEXT NOT NULL, `aliases` TEXT NOT NULL, `summary` TEXT NOT NULL, `details` TEXT NOT NULL, `dateLabel` TEXT NOT NULL, `placeLabel` TEXT NOT NULL, `sourceCitation` TEXT NOT NULL, `externalUrl` TEXT NOT NULL, `reviewStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_records_kind` ON `encyclopedia_records` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_records_reviewStatus` ON `encyclopedia_records` (`reviewStatus`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_encyclopedia_records_kind_normalizedTitle` ON `encyclopedia_records` (`kind`, `normalizedTitle`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_records_updatedAt` ON `encyclopedia_records` (`updatedAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `encyclopedia_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordId` INTEGER NOT NULL, `relationType` TEXT NOT NULL, `taziehUid` TEXT NOT NULL, `versionUid` TEXT NOT NULL, `roleUid` TEXT NOT NULL, `sectionUid` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`recordId`) REFERENCES `encyclopedia_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_links_recordId` ON `encyclopedia_links` (`recordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_links_taziehUid` ON `encyclopedia_links` (`taziehUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_links_versionUid` ON `encyclopedia_links` (`versionUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_links_roleUid` ON `encyclopedia_links` (`roleUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_links_sectionUid` ON `encyclopedia_links` (`sectionUid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_encyclopedia_links_recordId_taziehUid_versionUid_roleUid_sectionUid` ON `encyclopedia_links` (`recordId`, `taziehUid`, `versionUid`, `roleUid`, `sectionUid`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `encyclopedia_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordId` INTEGER NOT NULL, `action` TEXT NOT NULL, `beforeJson` TEXT NOT NULL, `afterJson` TEXT NOT NULL, `changedAt` INTEGER NOT NULL, `actor` TEXT NOT NULL, FOREIGN KEY(`recordId`) REFERENCES `encyclopedia_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_history_recordId` ON `encyclopedia_history` (`recordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_encyclopedia_history_changedAt` ON `encyclopedia_history` (`changedAt`)")
            }
        }

        /** Version 15: private group, member, performance, casting and director-note workspace. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `performance_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `city` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performance_groups_uid` ON `performance_groups` (`uid`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performance_groups_normalizedName` ON `performance_groups` (`normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_groups_updatedAt` ON `performance_groups` (`updatedAt`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `group_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `groupId` INTEGER NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, `contact` TEXT NOT NULL, `vocalRange` TEXT NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`groupId`) REFERENCES `performance_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_members_uid` ON `group_members` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_members_groupId_normalizedName` ON `group_members` (`groupId`, `normalizedName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_isActive` ON `group_members` (`isActive`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `performances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `groupId` INTEGER NOT NULL, `title` TEXT NOT NULL, `taziehUid` TEXT NOT NULL, `versionUid` TEXT NOT NULL, `scheduledAt` INTEGER, `venue` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`groupId`) REFERENCES `performance_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performances_uid` ON `performances` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performances_groupId` ON `performances` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performances_taziehUid` ON `performances` (`taziehUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performances_versionUid` ON `performances` (`versionUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performances_scheduledAt` ON `performances` (`scheduledAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performances_status` ON `performances` (`status`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `performance_assignments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `performanceId` INTEGER NOT NULL, `memberId` INTEGER NOT NULL, `roleUid` TEXT NOT NULL, `roleTitle` TEXT NOT NULL, `isAlternate` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `notes` TEXT NOT NULL, `assignedAt` INTEGER NOT NULL, FOREIGN KEY(`performanceId`) REFERENCES `performances`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `group_members`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_assignments_performanceId` ON `performance_assignments` (`performanceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_assignments_memberId` ON `performance_assignments` (`memberId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_performance_assignments_performanceId_roleUid_memberId` ON `performance_assignments` (`performanceId`, `roleUid`, `memberId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `performance_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `performanceId` INTEGER NOT NULL, `sectionUid` TEXT NOT NULL, `noteType` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`performanceId`) REFERENCES `performances`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_notes_performanceId` ON `performance_notes` (`performanceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_notes_sectionUid` ON `performance_notes` (`sectionUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_notes_updatedAt` ON `performance_notes` (`updatedAt`)")
            }
        }

        /**
         * Version 16:
         * Multi-role dialogue selection, verified turn metadata and stage cues.
         *
         * Existing dialogue turns are preserved.
         * Their rehearsal order is initialized from the existing orderIndex.
         * Their historical/source order is deliberately left unverified.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // Roles selected for a dialogue, tied to one exact archive version.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogue_role_selections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dialogueId` INTEGER NOT NULL,
                        `roleId` INTEGER NOT NULL,
                        `versionUid` TEXT NOT NULL,
                        `displayOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`dialogueId`)
                            REFERENCES `dialogues`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`roleId`)
                            REFERENCES `roles`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_dialogue_role_selections_dialogueId_roleId`
                    ON `dialogue_role_selections` (`dialogueId`, `roleId`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_role_selections_roleId`
                    ON `dialogue_role_selections` (`roleId`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_role_selections_versionUid`
                    ON `dialogue_role_selections` (`versionUid`)
                """.trimIndent())

                // Explicit metadata for source sequence, rehearsal order and reply links.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogue_turn_metadata` (
                        `turnId` INTEGER NOT NULL,
                        `sourceSequenceIndex` INTEGER,
                        `sourceSequenceStatus` TEXT NOT NULL,
                        `rehearsalOrderIndex` INTEGER NOT NULL,
                        `expectsResponse` INTEGER NOT NULL,
                        `replyToTurnId` INTEGER,
                        `expectedResponderRoleId` INTEGER,
                        `sequenceEvidence` TEXT NOT NULL,
                        `directorNote` TEXT NOT NULL,
                        PRIMARY KEY(`turnId`),
                        FOREIGN KEY(`turnId`)
                            REFERENCES `dialogue_turns`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`replyToTurnId`)
                            REFERENCES `dialogue_turns`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`expectedResponderRoleId`)
                            REFERENCES `roles`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_turn_metadata_replyToTurnId`
                    ON `dialogue_turn_metadata` (`replyToTurnId`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_turn_metadata_expectedResponderRoleId`
                    ON `dialogue_turn_metadata` (`expectedResponderRoleId`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_turn_metadata_sourceSequenceIndex`
                    ON `dialogue_turn_metadata` (`sourceSequenceIndex`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_turn_metadata_rehearsalOrderIndex`
                    ON `dialogue_turn_metadata` (`rehearsalOrderIndex`)
                """.trimIndent())

                // Stage directions and performance cues are stored separately
                // so the original source text is never rewritten.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dialogue_stage_cues` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `turnId` INTEGER NOT NULL,
                        `cueType` TEXT NOT NULL,
                        `cueText` TEXT NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `sourceStartOffset` INTEGER,
                        `sourceEndOffset` INTEGER,
                        `origin` TEXT NOT NULL,
                        `isVerified` INTEGER NOT NULL,
                        FOREIGN KEY(`turnId`)
                            REFERENCES `dialogue_turns`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_stage_cues_turnId_orderIndex`
                    ON `dialogue_stage_cues` (`turnId`, `orderIndex`)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogue_stage_cues_cueType`
                    ON `dialogue_stage_cues` (`cueType`)
                """.trimIndent())

                // Preserve every existing turn and initialize rehearsal order.
                // Do NOT claim that the old order is historically verified.
                db.execSQL("""
                    INSERT OR IGNORE INTO `dialogue_turn_metadata` (
                        `turnId`,
                        `sourceSequenceIndex`,
                        `sourceSequenceStatus`,
                        `rehearsalOrderIndex`,
                        `expectsResponse`,
                        `replyToTurnId`,
                        `expectedResponderRoleId`,
                        `sequenceEvidence`,
                        `directorNote`
                    )
                    SELECT
                        `id`,
                        NULL,
                        'UNVERIFIED',
                        `orderIndex`,
                        0,
                        NULL,
                        NULL,
                        '',
                        ''
                    FROM `dialogue_turns`
                """.trimIndent())
            }
        }

        /**
         * Version 17:
         * Add installation ownership for personal dialogues.
         * Existing dialogues remain preserved with an empty owner.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `dialogues`
                    ADD COLUMN `ownerInstallationId`
                    TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_dialogues_ownerInstallationId`
                    ON `dialogues` (`ownerInstallationId`)
                    """.trimIndent()
                )
            }
        }

        /**
         * Version 18: add an isolated director-office workspace.
         * Existing library, dialogue, rehearsal and performance data is untouched.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, `taziehUid` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_plans_uid` ON `director_plans` (`uid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plans_taziehUid` ON `director_plans` (`taziehUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plans_status` ON `director_plans` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plans_updatedAt` ON `director_plans` (`updatedAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_plan_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `versionNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `changeNotes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `publishedAt` INTEGER, FOREIGN KEY(`planId`) REFERENCES `director_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plan_versions_planId` ON `director_plan_versions` (`planId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_plan_versions_planId_versionNumber` ON `director_plan_versions` (`planId`, `versionNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_plan_versions_status` ON `director_plan_versions` (`status`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_source_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `originalName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `localPath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `pageCount` INTEGER, `importMethod` TEXT NOT NULL, `extractedText` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`planId`) REFERENCES `director_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_source_files_planId` ON `director_source_files` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_source_files_sha256` ON `director_source_files` (`sha256`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_source_files_createdAt` ON `director_source_files` (`createdAt`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_turns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `versionId` INTEGER NOT NULL, `turnNumber` INTEGER NOT NULL, `sequenceIndex` INTEGER NOT NULL, `turnType` TEXT NOT NULL, `title` TEXT NOT NULL, `continuityGroupUid` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`versionId`) REFERENCES `director_plan_versions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turns_versionId` ON `director_turns` (`versionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_turns_versionId_sequenceIndex` ON `director_turns` (`versionId`, `sequenceIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turns_versionId_turnNumber` ON `director_turns` (`versionId`, `turnNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turns_continuityGroupUid` ON `director_turns` (`continuityGroupUid`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_turn_participants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `turnId` INTEGER NOT NULL, `roleUid` TEXT NOT NULL, `roleTitle` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `isLead` INTEGER NOT NULL, FOREIGN KEY(`turnId`) REFERENCES `director_turns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turn_participants_turnId` ON `director_turn_participants` (`turnId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_turn_participants_turnId_roleUid` ON `director_turn_participants` (`turnId`, `roleUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turn_participants_roleUid` ON `director_turn_participants` (`roleUid`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_text_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `turnId` INTEGER NOT NULL, `segmentIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, `sectionUid` TEXT, `sourceFileId` INTEGER, `sourcePageStart` INTEGER, `sourcePageEnd` INTEGER, FOREIGN KEY(`turnId`) REFERENCES `director_turns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`sourceFileId`) REFERENCES `director_source_files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_text_segments_turnId` ON `director_text_segments` (`turnId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_text_segments_sourceFileId` ON `director_text_segments` (`sourceFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_text_segments_turnId_segmentIndex` ON `director_text_segments` (`turnId`, `segmentIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_text_segments_sectionUid` ON `director_text_segments` (`sectionUid`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_stage_cues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `versionId` INTEGER NOT NULL, `turnId` INTEGER, `cueType` TEXT NOT NULL, `title` TEXT NOT NULL, `instruction` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `blocksNextTurn` INTEGER NOT NULL, `continuationRoleUid` TEXT NOT NULL, FOREIGN KEY(`versionId`) REFERENCES `director_plan_versions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`turnId`) REFERENCES `director_turns`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_stage_cues_versionId` ON `director_stage_cues` (`versionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_stage_cues_turnId` ON `director_stage_cues` (`turnId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_stage_cues_versionId_orderIndex` ON `director_stage_cues` (`versionId`, `orderIndex`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `versionId` INTEGER, `performanceUid` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `notes` TEXT NOT NULL, FOREIGN KEY(`planId`) REFERENCES `director_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`versionId`) REFERENCES `director_plan_versions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_sessions_planId` ON `director_sessions` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_sessions_versionId` ON `director_sessions` (`versionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_sessions_startedAt` ON `director_sessions` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_sessions_status` ON `director_sessions` (`status`)")

                db.execSQL("""CREATE TABLE IF NOT EXISTS `director_turn_progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `turnId` INTEGER NOT NULL, `status` TEXT NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, `note` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `director_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`turnId`) REFERENCES `director_turns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turn_progress_sessionId` ON `director_turn_progress` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turn_progress_turnId` ON `director_turn_progress` (`turnId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_director_turn_progress_sessionId_turnId` ON `director_turn_progress` (`sessionId`, `turnId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_director_turn_progress_status` ON `director_turn_progress` (`status`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookapp.db"
                )
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19
                    )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Everything below this point is preserved from the previous AppDatabase.kt.
// -----------------------------------------------------------------------------

suspend fun syncLocalContentFiles(context: Context, db: AppDatabase): Int {
    val isProtectedViewer = com.example.bookapp.BuildConfig.PUBLIC_VIEWER

    val names = context.assets.list("content")
        ?.filter { name ->
            if (isProtectedViewer) {
                name.endsWith(".taz")
            } else {
                name.endsWith(".json") && name != "manifest.json"
            }
        }
        ?.sorted() ?: emptyList()

    val allFiles = names.map { name ->
        val text = if (isProtectedViewer) {
            ContentProtectionHelper.readProtectedJson(context, name)
        } else {
            context.assets.open("content/$name")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }

        FileWithKey(name, text, "$name:${sha256(text)}", "")
    }

    var processed = Prefs.getProcessedContentFiles(context)
    val hadContentBefore = db.fieldDao().getAll().isNotEmpty()

    if (!hadContentBefore && processed.isNotEmpty()) {
        processed = emptySet()
    }

    val newOrChangedFiles = allFiles.filter { it.key !in processed }

    if (newOrChangedFiles.isEmpty()) return 0

    for (file in newOrChangedFiles) {
        val errors = ContentValidator.validate(file.text)

        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(
                "محتوای ${file.name} نامعتبر است: ${errors.take(3).joinToString("؛ ")}"
            )
        }

        mergeContentFromJson(db, file.text, ContentUid.source(file.name))
    }

    Prefs.setProcessedContentFiles(context, allFiles.map { it.key }.toSet())

    return newOrChangedFiles.size
}

private data class FileWithKey(
    val name: String,
    val text: String,
    val key: String,
    val legacyKey: String
)

suspend fun syncRemoteContent(
    db: AppDatabase,
    url: String
): Result<Unit> {

    if (com.example.bookapp.BuildConfig.PUBLIC_VIEWER) {
        return Result.failure(
            IllegalStateException("نسخه عمومی اجازه دریافت محتوای آنلاین را ندارد")
        )
    }

    val trimmedUrl = url.trim()

    if (trimmedUrl.isBlank()) {
        return Result.failure(
            IllegalStateException("ابتدا آدرس محتوا را در «بروزرسانی محتوا» وارد و ذخیره کنید.")
        )
    }

    if (!trimmedUrl.startsWith("https://")) {
        return Result.failure(
            IllegalStateException("آدرس محتوا باید با https:// شروع شود.")
        )
    }

    return try {
        val jsonText = kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO
        ) {
            withHttpGet(trimmedUrl)
        }

        val errors = ContentValidator.validate(jsonText)

        if (errors.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "محتوای آنلاین نامعتبر است: ${errors.take(3).joinToString("؛ ")}"
                )
            )
        }

        mergeContentFromJson(
            db,
            jsonText,
            ContentUid.source(trimmedUrl)
        )

        Result.success(Unit)

    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun withHttpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection

    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.requestMethod = "GET"

    return try {
        connection.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/**
 * Imports content by stable UID.
 *
 * Legacy JSON without UIDs remains supported by title matching.
 * A source UID lets us safely remove sections deleted from a particular
 * content file without touching sections supplied by other files.
 */
internal suspend fun mergeContentFromJson(
    db: AppDatabase,
    jsonText: String,
    sourceUid: String = ContentUid.source(jsonText),
    syncContext: Context? = null
) {
    val fields = JSONArray(jsonText)

    db.withTransaction {
        for (fi in 0 until fields.length()) {
            val fieldObj = fields.getJSONObject(fi)
            val fieldTitle = fieldObj.getString("title")
            val explicitFieldUid = fieldObj.optString("uid").trim()

            val fieldUid = explicitFieldUid.ifBlank {
                ContentUid.derived("field", "root", fieldTitle)
            }

            val remoteFieldUpdatedAt = fieldObj.optString("updatedAt")

            val applyField = syncContext == null ||
                SyncMetaStore.shouldApplyRemote(
                    syncContext,
                    "fields",
                    fieldUid,
                    remoteFieldUpdatedAt
                )

            val existingField = db.fieldDao().getByUid(fieldUid)
                ?: if (explicitFieldUid.isBlank()) {
                    db.fieldDao().getByTitle(fieldTitle)
                } else {
                    null
                }

            val fieldId = if (existingField == null) {
                val insertedId = db.fieldDao().insert(
                    FieldEntity(
                        title = fieldTitle,
                        uid = fieldUid
                    )
                )

                if (syncContext != null) {
                    SyncMetaStore.baseline(
                        syncContext,
                        "fields",
                        fieldUid,
                        fieldObj,
                        remoteFieldUpdatedAt,
                        fieldObj.optString("createdAt")
                    )
                }

                insertedId
            } else {
                if (
                    applyField &&
                    (existingField.uid != fieldUid || existingField.title != fieldTitle)
                ) {
                    db.fieldDao().updateIdentity(
                        existingField.id,
                        fieldTitle,
                        fieldUid
                    )
                }

                if (syncContext != null && applyField) {
                    SyncMetaStore.baseline(
                        syncContext,
                        "fields",
                        fieldUid,
                        fieldObj,
                        remoteFieldUpdatedAt,
                        fieldObj.optString("createdAt")
                    )
                }

                existingField.id
            }

            val taziehs = fieldObj.getJSONArray("taziehs")

            for (ti in 0 until taziehs.length()) {
                val taziehObj = taziehs.getJSONObject(ti)
                val taziehTitle = taziehObj.getString("title")
                val explicitTaziehUid = taziehObj.optString("uid").trim()

                val taziehUid = explicitTaziehUid.ifBlank {
                    ContentUid.derived("tazieh", fieldUid, taziehTitle)
                }

                val remoteTaziehUpdatedAt = taziehObj.optString("updatedAt")

                val applyTazieh = syncContext == null ||
                    SyncMetaStore.shouldApplyRemote(
                        syncContext,
                        "taziehs",
                        taziehUid,
                        remoteTaziehUpdatedAt
                    )

                val existingTazieh = db.taziehDao().getByUid(taziehUid)
                    ?: if (explicitTaziehUid.isBlank()) {
                        db.taziehDao().getByTitle(fieldId, taziehTitle)
                    } else {
                        null
                    }

                val taziehId = if (existingTazieh == null) {
                    val insertedId = db.taziehDao().insert(
                        TaziehEntity(
                            fieldId = fieldId,
                            title = taziehTitle,
                            uid = taziehUid
                        )
                    )

                    if (syncContext != null) {
                        SyncMetaStore.baseline(
                            syncContext,
                            "taziehs",
                            taziehUid,
                            taziehObj,
                            remoteTaziehUpdatedAt,
                            taziehObj.optString("createdAt")
                        )
                    }

                    insertedId
                } else {
                    if (
                        applyTazieh &&
                        (existingTazieh.uid != taziehUid ||
                            existingTazieh.title != taziehTitle)
                    ) {
                        db.taziehDao().updateIdentity(
                            existingTazieh.id,
                            fieldId,
                            taziehTitle,
                            taziehUid
                        )
                    }

                    if (syncContext != null && applyTazieh) {
                        SyncMetaStore.baseline(
                            syncContext,
                            "taziehs",
                            taziehUid,
                            taziehObj,
                            remoteTaziehUpdatedAt,
                            taziehObj.optString("createdAt")
                        )
                    }

                    existingTazieh.id
                }

                val author = taziehObj.optString("author", "").ifBlank { null }
                val authorEmail = taziehObj.optString("authorEmail", "").ifBlank { null }

                if (applyTazieh && (author != null || authorEmail != null)) {
                    db.taziehDao().updateAuthor(
                        taziehId,
                        author ?: existingTazieh?.author,
                        authorEmail ?: existingTazieh?.authorEmail
                    )
                }

                // Every imported Tazieh gets a version container.
                // Existing installations are backfilled by MIGRATION_10_11.
                var currentArchiveVersion =
                    db.archiveDao().currentVersion(taziehUid)

                if (currentArchiveVersion == null) {
                    val now = System.currentTimeMillis()

                    val version =
                        com.example.bookapp.data.archive.TaziehVersionEntity(
                            uid = "import-version-${ContentUid.new()}",
                            taziehUid = taziehUid,
                            versionNumber =
                                db.archiveDao().maxVersionNumber(taziehUid) + 1,
                            title = "نسخه واردشده اولیه",
                            status = com.example.bookapp.data.archive.TaziehVersionEntity.STATUS_LEGACY_UNREVIEWED,
                            createdAt = now,
                            updatedAt = now,
                            isCurrent = true,
                            changeNote = "ایجاد خودکار هنگام ورود محتوا؛ نیازمند تطبیق با سند اصلی"
                        )

                    db.archiveDao().insertVersion(version)
                    currentArchiveVersion = version
                }

                val currentVersionUid = currentArchiveVersion.uid

                val roles = taziehObj.getJSONArray("roles")

                for (ri in 0 until roles.length()) {
                    val roleObj = roles.getJSONObject(ri)
                    val roleTitle = roleObj.getString("title")
                    val explicitRoleUid = roleObj.optString("uid").trim()

                    val roleUid = explicitRoleUid.ifBlank {
                        ContentUid.derived("role", taziehUid, roleTitle)
                    }

                    val remoteRoleUpdatedAt = roleObj.optString("updatedAt")

                    val applyRole = syncContext == null ||
                        SyncMetaStore.shouldApplyRemote(
                            syncContext,
                            "roles",
                            roleUid,
                            remoteRoleUpdatedAt
                        )

                    val roleOrder = ri

                    val existingRole = db.roleDao().getByUid(roleUid)
                        ?: if (explicitRoleUid.isBlank()) {
                            db.roleDao().getByTitle(taziehId, roleTitle)
                        } else {
                            null
                        }

                    val roleId = if (existingRole == null) {
                        val insertedId = db.roleDao().insert(
                            RoleEntity(
                                taziehId = taziehId,
                                title = roleTitle,
                                orderIndex = roleOrder,
                                uid = roleUid,
                                versionUid = currentVersionUid
                            )
                        )

                        if (syncContext != null) {
                            SyncMetaStore.baseline(
                                syncContext,
                                "roles",
                                roleUid,
                                roleObj,
                                remoteRoleUpdatedAt,
                                roleObj.optString("createdAt")
                            )
                        }

                        insertedId
                    } else {
                        if (applyRole) {
                            db.roleDao().updateFromContent(
                                existingRole.id,
                                taziehId,
                                roleTitle,
                                roleOrder,
                                roleUid,
                                currentVersionUid
                            )
                        }

                        if (syncContext != null && applyRole) {
                            SyncMetaStore.baseline(
                                syncContext,
                                "roles",
                                roleUid,
                                roleObj,
                                remoteRoleUpdatedAt,
                                roleObj.optString("createdAt")
                            )
                        }

                        existingRole.id
                    }

                    val sections = roleObj.getJSONArray("sections")
                    val seenSectionUids = mutableSetOf<String>()

                    for (si in 0 until sections.length()) {
                        val secObj = sections.getJSONObject(si)
                        val sectionTitle = secObj.getString("title")
                        val explicitSectionUid = secObj.optString("uid").trim()

                        val sectionUid = explicitSectionUid.ifBlank {
                            ContentUid.derivedWithIndex(
                                "section",
                                roleUid,
                                sectionTitle,
                                si
                            )
                        }

                        val newContent = secObj.getString("content")
                        val newAudio = secObj.optString("audio", "").ifBlank { null }
                        val remoteSectionUpdatedAt = secObj.optString("updatedAt")

                        val applySection = syncContext == null ||
                            SyncMetaStore.shouldApplyRemote(
                                syncContext,
                                "sections",
                                sectionUid,
                                remoteSectionUpdatedAt
                            )

                        seenSectionUids += sectionUid

                        val existingSection = db.sectionDao().getByUid(sectionUid)
                            ?: if (explicitSectionUid.isBlank()) {
                                db.sectionDao().getByTitle(roleId, sectionTitle)
                            } else {
                                null
                            }

                        if (existingSection == null) {
                            db.sectionDao().insert(
                                SectionEntity(
                                    roleId = roleId,
                                    orderIndex = si,
                                    title = sectionTitle,
                                    content = newContent,
                                    audioUrl = newAudio,
                                    uid = sectionUid,
                                    sourceUid = sourceUid
                                )
                            )

                            if (syncContext != null) {
                                SyncMetaStore.baseline(
                                    syncContext,
                                    "sections",
                                    sectionUid,
                                    secObj,
                                    remoteSectionUpdatedAt,
                                    secObj.optString("createdAt")
                                )
                            }
                        } else {
                            if (applySection) {
                                db.sectionDao().updateFromContent(
                                    id = existingSection.id,
                                    roleId = roleId,
                                    title = sectionTitle,
                                    content = newContent,
                                    audioUrl = newAudio ?: existingSection.audioUrl,
                                    orderIndex = si,
                                    uid = sectionUid,
                                    sourceUid = sourceUid
                                )

                                if (syncContext != null) {
                                    SyncMetaStore.baseline(
                                        syncContext,
                                        "sections",
                                        sectionUid,
                                        secObj,
                                        remoteSectionUpdatedAt,
                                        secObj.optString("createdAt")
                                    )
                                }
                            }
                        }
                    }

                    // Only sections owned by this source are candidates for removal.
                    if (syncContext == null) {
                        db.sectionDao()
                            .getByRole(roleId)
                            // Remove obsolete sections owned by any prior revision of
                            // this majlis. Empty sourceUid denotes user-created/local data.
                            .filter { it.sourceUid.isNotBlank() && it.uid !in seenSectionUids }
                            .forEach { db.sectionDao().delete(it.id) }
                    }
                }
            }
        }
    }
}

private fun sha256(text: String): String = ContentUid.sha256(text)
