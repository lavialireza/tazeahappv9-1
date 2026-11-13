package com.example.bookapp.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase

/** Read-only diagnostics. It never repairs, deletes, migrates, or rewrites user data. */
data class DatabaseHealthReport(
    val integrityResult: String,
    val foreignKeyViolations: Int,
    val userVersion: Int,
    val missingTables: List<String>,
    val databaseBytes: Long
) {
    val isHealthy: Boolean
        get() = integrityResult.equals("ok", ignoreCase = true) &&
            foreignKeyViolations == 0 && userVersion == APP_DATABASE_SCHEMA_VERSION &&
            missingTables.isEmpty()

    fun toPersianSummary(): String = buildString {
        append(if (isHealthy) "سلامت پایگاه داده تأیید شد." else "هشدار: نیاز به بررسی پایگاه داده وجود دارد.")
        append("\nنتیجه SQLite: ").append(integrityResult)
        append("\nخطاهای ارتباطی: ").append(foreignKeyViolations)
        append("\nنسخه پایگاه داده: ").append(userVersion).append(" از ").append(APP_DATABASE_SCHEMA_VERSION)
        append("\nحجم فایل: ").append(databaseBytes).append(" بایت")
        if (missingTables.isNotEmpty()) append("\nجدول‌های مفقود: ").append(missingTables.joinToString("، "))
    }
}

private val REQUIRED_DATABASE_TABLES = setOf(
    "fields", "taziehs", "roles", "sections", "notes", "footnotes", "dialogues",
    "dialogue_turns", "tazieh_images", "bookmarks", "section_tags", "recent_sections",
    "reading_history", "my_roles", "active_days", "tazieh_versions", "source_documents",
    "glossary_terms", "glossary_spellings", "glossary_meanings", "glossary_history",
    "encyclopedia_records", "encyclopedia_links", "encyclopedia_history",
    "performance_groups", "group_members", "performances", "performance_assignments", "performance_notes"
)

/** Runs SQLite integrity_check + foreign_key_check and verifies expected schema tables. */
fun runDatabaseHealthCheck(context: Context, db: AppDatabase): DatabaseHealthReport {
    val sqlite: SupportSQLiteDatabase = db.openHelper.writableDatabase
    val integrity = sqlite.query("PRAGMA integrity_check").use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) ?: "نتیجه خالی" else "نتیجه خالی"
    }
    val fkViolations = sqlite.query("PRAGMA foreign_key_check").use { cursor ->
        var count = 0
        while (cursor.moveToNext()) count++
        count
    }
    val userVersion = sqlite.query("PRAGMA user_version").use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else -1
    }
    val existingTables = sqlite.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    ).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    val dbName = db.openHelper.databaseName
    val bytes = if (dbName.isNullOrBlank()) 0L else context.getDatabasePath(dbName).length()
    return DatabaseHealthReport(
        integrityResult = integrity,
        foreignKeyViolations = fkViolations,
        userVersion = userVersion,
        missingTables = (REQUIRED_DATABASE_TABLES - existingTables).toList(),
        databaseBytes = bytes
    )
}
