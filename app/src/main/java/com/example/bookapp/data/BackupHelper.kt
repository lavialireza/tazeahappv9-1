package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import com.example.bookapp.BuildConfig
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Version 2 portable backup. Relationships are stored by stable UID rather than SQLite IDs,
 * so the backup can be restored into a database whose auto-generated IDs are different.
 */
suspend fun buildBackupJson(context: Context, db: AppDatabase): String {
    // Keep the Room copy current while legacy UI code still writes SharedPreferences.
    migratePrefsUserDataToRoom(context, db)
    return db.withTransaction { buildBackupJsonInTransaction(context, db) }
}

private suspend fun buildBackupJsonInTransaction(context: Context, db: AppDatabase): String {
    val root = JSONObject().apply {
        put("app", "taziehapp")
        put("backupVersion", 4)
        put("schemaVersion", APP_DATABASE_SCHEMA_VERSION)
        put("createdAt", System.currentTimeMillis())
        // This portable file contains user-specific records only; it is not a full
        // database/content/archive image. Keep the scope explicit for safe restore.
        put("scope", "personal_user_data")
        put(
            "includedData",
            JSONArray(
                buildList {
                    addAll(
                        listOf(
                            "notes", "bookmarks", "recent", "readSections",
                            "activeDays", "tags", "footnotes", "myRoles"
                        )
                    )
                    if (!BuildConfig.PUBLIC_VIEWER) add("dialogues")
                }
            )
        )
        put(
            "dialogueDataScope",
            if (BuildConfig.PUBLIC_VIEWER) "device_local_only" else "admin_archive"
        )
    }

    root.put("notes", JSONArray().apply {
        db.noteDao().getAll().forEach { note ->
            put(JSONObject().apply {
                put("uid", note.uid)
                put("title", note.title)
                put("content", note.content)
                note.sectionUid?.let { put("sectionUid", it) }
                put("createdAt", note.createdAt)
            })
        }
    })

    val sectionsById = mutableMapOf<Long, SectionEntity>()
    val taziehsById = mutableMapOf<Long, TaziehEntity>()
    val rolesById = mutableMapOf<Long, RoleEntity>()
    db.fieldDao().getAll().forEach { field ->
        db.taziehDao().getByField(field.id).forEach { tazieh ->
            taziehsById[tazieh.id] = tazieh
            db.roleDao().getByTazieh(tazieh.id).forEach { role ->
                rolesById[role.id] = role
                db.sectionDao().getByRole(role.id).forEach { section -> sectionsById[section.id] = section }
            }
        }
    }

    root.put("bookmarks", JSONArray().apply {
        db.userDataDao().getBookmarks().map { it.sectionUid }.forEach(::put)
    })

    root.put("recent", JSONArray().apply {
        Prefs.getRecent(context).mapNotNull { sectionsById[it]?.uid }.forEach(::put)
    })

    root.put("readSections", JSONArray().apply {
        db.userDataDao().getReadingHistory().map { it.sectionUid }.forEach(::put)
    })

    root.put("activeDays", JSONArray().apply { db.userDataDao().getActiveDays().map { it.dayKey }.forEach(::put) })

    root.put("tags", JSONArray().apply {
        db.userDataDao().getTags().forEach { tag ->
            put(JSONObject().apply { put("sectionUid", tag.sectionUid); put("tag", tag.tag) })
        }
    })

    root.put("footnotes", JSONArray().apply {
        sectionsById.values.forEach { section ->
            db.footnoteDao().getBySection(section.id).forEach { fn ->
                put(JSONObject().apply {
                    put("uid", fn.uid)
                    put("sectionUid", section.uid)
                    put("term", fn.term)
                    put("explanation", fn.explanation)
                })
            }
        }
    })

    root.put("myRoles", JSONArray().apply {
        db.userDataDao().getMyRoles().forEach { role ->
            put(JSONObject().apply {
                put("taziehUid", role.taziehUid)
                put("roleUid", role.roleUid)
            })
        }
    })

    root.put("dialogues", JSONArray().apply {
        // گفت‌وگوهای Viewer دادهٔ محلی همان نصب هستند و نباید در فایل
        // پشتیبان قابل انتقال یا آرشیو Admin قرار بگیرند.
        if (!BuildConfig.PUBLIC_VIEWER) {
            taziehsById.values.forEach { tazieh ->
                db.dialogueDao().getByTazieh(tazieh.id)
                    .filter { it.ownerInstallationId.isBlank() }
                    .forEach { dialogue ->
                        val turns = JSONArray().apply {
                            db.dialogueTurnDao().getByDialogue(dialogue.id).forEach { turn ->
                                sectionsById[turn.sectionId]?.let { section ->
                                    val metadata = db.dialogueTurnMetadataDao()
                                        .getByTurn(turn.id)
                                    put(JSONObject().apply {
                                        put("uid", turn.uid)
                                        put("sectionUid", section.uid)
                                        put("orderIndex", turn.orderIndex)
                                        put(
                                            "rehearsalOrderIndex",
                                            metadata?.rehearsalOrderIndex ?: turn.orderIndex
                                        )
                                    })
                                }
                            }
                        }
                        val roleSelections = JSONArray().apply {
                            db.dialogueRoleSelectionDao()
                                .getByDialogue(dialogue.id)
                                .forEach { selection ->
                                    val role = rolesById[selection.roleId]
                                        ?: db.roleDao().getById(selection.roleId)
                                    put(JSONObject().apply {
                                        put("roleUid", role.uid)
                                        put("versionUid", selection.versionUid)
                                        put("displayOrder", selection.displayOrder)
                                    })
                                }
                        }
                        put(JSONObject().apply {
                            put("uid", dialogue.uid)
                            put("taziehUid", tazieh.uid)
                            put("title", dialogue.title)
                            put("versionUid", dialogue.versionUid)
                            put("roleSelections", roleSelections)
                            put("turns", turns)
                        })
                    }
            }
        }
    })

    root.put("integritySha256", ContentUid.sha256(canonicalJson(root)))
    return root.toString(2)
}

private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
private const val MAX_BACKUP_ITEMS = 200_000

private fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
        JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
    }
    is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index -> canonicalJson(value.get(index)) }
    JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

private fun readBounded(input: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "حجم فایل پشتیبان از حد مجاز ۵۰ مگابایت بیشتر است." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun validateBackupRoot(root: JSONObject): Int {
    val app = root.optString("app", "taziehapp")
    require(app == "taziehapp") { "این فایل، پشتیبان برنامه تعزیه نیست." }
    val version = root.optInt("backupVersion", 1)
    require(version in 1..4) { "نسخه پشتیبان پشتیبانی نمی‌شود: $version" }
    val schema = root.optInt("schemaVersion", 10)
    require(schema in 1..APP_DATABASE_SCHEMA_VERSION) { "نسخه ساختار پشتیبان جدیدتر از این برنامه است." }

    val checksum = root.optString("integritySha256", "")
    if (version >= 4) {
        require(checksum.matches(Regex("[0-9a-f]{64}"))) { "کد سلامت فایل پشتیبان وجود ندارد یا نامعتبر است." }
        val unsigned = JSONObject(root.toString()).apply { remove("integritySha256") }
        require(ContentUid.sha256(canonicalJson(unsigned)) == checksum) { "فایل پشتیبان آسیب دیده یا محتوای آن تغییر کرده است." }
        require(root.optString("scope") == "personal_user_data") { "دامنه اطلاعات پشتیبان ناشناخته است." }
    }

    var totalItems = 0
    val arrayKeys = listOf("notes", "bookmarks", "recent", "readSections", "activeDays", "tags", "footnotes", "myRoles", "dialogues")
    arrayKeys.forEach { key ->
        if (root.has(key) && !root.isNull(key)) {
            val array = root.optJSONArray(key) ?: throw IllegalArgumentException("ساختار بخش $key در فایل پشتیبان معتبر نیست.")
            totalItems += array.length()
            require(totalItems <= MAX_BACKUP_ITEMS) { "تعداد رکوردهای فایل پشتیبان بیش از حد مجاز است." }
        }
    }
    val notes = root.optJSONArray("notes") ?: JSONArray()
    for (i in 0 until notes.length()) {
        val note = notes.optJSONObject(i) ?: throw IllegalArgumentException("رکورد یادداشت شماره ${i + 1} معتبر نیست.")
        require(note.has("title") && note.has("content")) { "اطلاعات یک یادداشت ناقص است." }
    }
    return version
}

suspend fun writeBackupToUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null) {
    val json = buildBackupJson(context, db)
    val bytes = if (password.isNullOrBlank()) json.toByteArray(Charsets.UTF_8) else encryptBackupText(json, password)
    require(bytes.size <= MAX_BACKUP_BYTES) { "حجم پشتیبان از حد مجاز ۵۰ مگابایت بیشتر است." }
    context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(bytes); stream.flush() }
        ?: throw IllegalStateException("فایل پشتیبان قابل نوشتن نیست")
}

suspend fun restoreBackupFromUri(context: Context, db: AppDatabase, uri: Uri, password: String? = null): Result<Unit> {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: return Result.failure(IllegalStateException("فایل خوانده نشد"))
        val text = if (password.isNullOrBlank()) String(bytes, Charsets.UTF_8) else decryptBackupBytes(bytes, password)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "حجم محتوای پشتیبان بیش از حد مجاز است." }
        val root = JSONObject(text)
        val version = validateBackupRoot(root)

        db.withTransaction {
            restoreNotes(db, root.optJSONArray("notes") ?: JSONArray())
            restoreBookmarks(context, db, root.optJSONArray("bookmarks") ?: JSONArray(), version)
            restoreRecent(context, db, root.optJSONArray("recent") ?: JSONArray(), version)
            restoreReadSections(context, db, root.optJSONArray("readSections") ?: JSONArray(), version)
            restoreActiveDays(context, db, root.optJSONArray("activeDays") ?: JSONArray())
            restoreTags(context, db, root.optJSONArray("tags") ?: JSONArray(), version)
            restoreFootnotes(db, root.optJSONArray("footnotes") ?: JSONArray(), version)
            restoreMyRoles(context, db, root.optJSONArray("myRoles") ?: JSONArray(), version)
            // Viewer گفتگوهای شخصی را از پشتیبان قابل انتقال وارد نمی‌کند؛
            // این داده‌ها فقط متعلق به نصب سازنده هستند.
            if (!BuildConfig.PUBLIC_VIEWER &&
                root.optString("dialogueDataScope") != "device_local_only"
            ) {
                restoreDialogues(db, root.optJSONArray("dialogues") ?: JSONArray(), version)
            }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private suspend fun restoreNotes(db: AppDatabase, arr: JSONArray) {
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val uid = o.optString("uid").ifBlank { ContentUid.sha256("legacy-note|${o.optString("title")} | ${o.optString("content")} | ${o.optLong("createdAt", 0L)}") }
        val existing = db.noteDao().getByUid(uid)
        val note = NoteEntity(
            id = existing?.id ?: 0,
            uid = uid,
            title = o.getString("title"),
            content = o.getString("content"),
            sectionUid = o.optString("sectionUid").ifBlank { existing?.sectionUid },
            createdAt = o.optLong("createdAt", existing?.createdAt ?: System.currentTimeMillis())
        )
        if (existing == null) db.noteDao().insert(note) else db.noteDao().update(note)
    }
}

private suspend fun resolveSectionId(db: AppDatabase, value: String, version: Int): Long? {
    return if (version >= 2) db.sectionDao().getByUid(value)?.id else value.toLongOrNull()
}

private suspend fun resolveTaziehId(db: AppDatabase, value: String, version: Int): Long? {
    return if (version >= 2) db.taziehDao().getByUid(value)?.id else value.toLongOrNull()
}

private suspend fun resolveRoleId(db: AppDatabase, value: String, version: Int): Long? {
    return if (version >= 2) db.roleDao().getByUid(value)?.id else value.toLongOrNull()
}

private suspend fun restoreBookmarks(context: Context, db: AppDatabase, arr: JSONArray, version: Int) {
    for (i in 0 until arr.length()) {
        val value = arr.get(i).toString()
        val id = resolveSectionId(db, value, version) ?: continue
        if (!Prefs.isBookmarked(context, id)) Prefs.toggleBookmark(context, id)
        db.userDataDao().getBookmark(db.sectionDao().getById(id).uid) ?: db.userDataDao().insertBookmark(BookmarkEntity(sectionUid = db.sectionDao().getById(id).uid))
    }
}

private suspend fun restoreRecent(context: Context, db: AppDatabase, arr: JSONArray, version: Int) {
    // Add from oldest to newest because Prefs.addRecent puts each item at the front.
    for (i in arr.length() - 1 downTo 0) {
        val id = resolveSectionId(db, arr.get(i).toString(), version) ?: continue
        Prefs.addRecent(context, id)
        val uid = db.sectionDao().getById(id).uid
        val existing = db.userDataDao().getRecentSections().firstOrNull { it.sectionUid == uid }
        if (existing == null) db.userDataDao().insertRecent(RecentSectionEntity(sectionUid = uid, position = i))
    }
}

private suspend fun restoreReadSections(context: Context, db: AppDatabase, arr: JSONArray, version: Int) {
    val ids = mutableSetOf<Long>()
    for (i in 0 until arr.length()) {
        resolveSectionId(db, arr.get(i).toString(), version)?.let(ids::add)
    }
    Prefs.mergeReadSectionIds(context, ids)
    ids.forEach { id ->
        db.sectionDao().getById(id).let { section ->
            if (db.userDataDao().getReading(section.uid) == null) db.userDataDao().insertReading(ReadingHistoryEntity(sectionUid = section.uid))
        }
    }
}

private suspend fun restoreActiveDays(context: Context, db: AppDatabase, arr: JSONArray) {
    val values = (0 until arr.length()).map { arr.get(it).toString() }.toSet()
    Prefs.mergeActiveDayValues(context, values)
    values.forEach { if (db.userDataDao().getActiveDay(it) == null) db.userDataDao().insertActiveDay(ActiveDayEntity(dayKey = it)) }
}

private suspend fun restoreTags(context: Context, db: AppDatabase, arr: JSONArray, version: Int) {
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val key = if (version >= 2) o.optString("sectionUid") else o.optString("sectionId")
        val id = resolveSectionId(db, key, version) ?: continue
        Prefs.setTag(context, id, o.getString("tag"))
        val uid = db.sectionDao().getById(id).uid
        val tag = o.getString("tag")
        if (db.userDataDao().getTag(uid) == null) db.userDataDao().insertTag(SectionTagEntity(sectionUid = uid, tag = tag)) else db.userDataDao().updateTag(uid, tag, System.currentTimeMillis())
    }
}

private suspend fun restoreFootnotes(db: AppDatabase, arr: JSONArray, version: Int) {
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val sectionKey = if (version >= 2) o.optString("sectionUid") else o.optString("sectionId")
        val sectionId = resolveSectionId(db, sectionKey, version) ?: continue
        val uid = o.optString("uid").ifBlank { ContentUid.new() }
        val existing = db.footnoteDao().getByUid(uid)
        val fn = FootnoteEntity(
            id = existing?.id ?: 0,
            sectionId = sectionId,
            term = o.getString("term"),
            explanation = o.getString("explanation"),
            uid = uid
        )
        if (existing == null) db.footnoteDao().insert(fn) else db.footnoteDao().update(fn)
    }
}

private suspend fun restoreMyRoles(context: Context, db: AppDatabase, arr: JSONArray, version: Int) {
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val taziehKey = if (version >= 2) o.optString("taziehUid") else o.optString("taziehId")
        val roleKey = if (version >= 2) o.optString("roleUid") else o.optString("roleId")
        val taziehId = resolveTaziehId(db, taziehKey, version) ?: continue
        val roleId = resolveRoleId(db, roleKey, version) ?: continue
        Prefs.setMyRole(context, taziehId, roleId)
        val tazieh = db.taziehDao().getById(taziehId)!!
        val role = db.roleDao().getById(roleId)
        if (db.userDataDao().getMyRole(tazieh.uid) == null) db.userDataDao().insertMyRole(MyRoleEntity(taziehUid = tazieh.uid, roleUid = role.uid)) else db.userDataDao().updateMyRole(tazieh.uid, role.uid, System.currentTimeMillis())
    }
}

private suspend fun restoreDialogues(db: AppDatabase, arr: JSONArray, version: Int) {
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val taziehKey = if (version >= 2) o.optString("taziehUid") else o.optString("taziehId")
        val taziehId = resolveTaziehId(db, taziehKey, version) ?: continue
        val uid = o.optString("uid").ifBlank { ContentUid.new() }
        val existing = db.dialogueDao().getByUid(uid)
        val versionUid = o.optString("versionUid").ifBlank {
            existing?.versionUid.orEmpty()
        }
        val dialogueId = if (existing == null) {
            db.dialogueDao().insert(
                DialogueEntity(
                    taziehId = taziehId,
                    title = o.getString("title"),
                    uid = uid,
                    versionUid = versionUid,
                    ownerInstallationId = ""
                )
            )
        } else {
            db.dialogueDao().updateFromBackup(
                dialogueId = existing.id,
                taziehId = taziehId,
                title = o.getString("title"),
                uid = uid,
                versionUid = versionUid
            )
            existing.id
        }

        // نقش‌های منبع مستقل، در صورت وجود در پشتیبان، به همان ترتیب بازسازی می‌شوند.
        if (o.has("roleSelections")) {
            db.dialogueRoleSelectionDao().deleteAllForDialogue(dialogueId)
            val roleSelections = o.optJSONArray("roleSelections") ?: JSONArray()
            for (j in 0 until roleSelections.length()) {
                val selection = roleSelections.optJSONObject(j) ?: continue
                val roleUid = selection.optString("roleUid")
                val selectedVersionUid = selection.optString("versionUid")
                if (roleUid.isBlank() || selectedVersionUid.isBlank()) continue
                val role = db.roleDao().getByUid(roleUid) ?: continue
                if (role.taziehId != taziehId ||
                    role.versionUid != selectedVersionUid
                ) continue
                db.dialogueRoleSelectionDao().insert(
                    DialogueRoleSelectionEntity(
                        dialogueId = dialogueId,
                        roleId = role.id,
                        versionUid = selectedVersionUid,
                        displayOrder = selection.optInt("displayOrder", j)
                    )
                )
            }
        }

        // A dialogue is a snapshot; replace its turns atomically to avoid duplicate turns.
        db.dialogueTurnDao().deleteAllForDialogue(dialogueId)
        val turnsArr = o.optJSONArray("turns") ?: JSONArray()
        for (j in 0 until turnsArr.length()) {
            val t = turnsArr.getJSONObject(j)
            val sectionKey = if (version >= 2) t.optString("sectionUid") else t.optString("sectionId")
            val sectionId = resolveSectionId(db, sectionKey, version) ?: continue
            val turnId = db.dialogueTurnDao().insert(
                DialogueTurnEntity(
                    dialogueId = dialogueId,
                    sectionId = sectionId,
                    orderIndex = t.getInt("orderIndex"),
                    uid = t.optString("uid").ifBlank { ContentUid.new() }
                )
            )
            if (turnId > 0L) {
                db.dialogueTurnMetadataDao().upsert(
                    DialogueTurnMetadataEntity(
                        turnId = turnId,
                        rehearsalOrderIndex = t.optInt(
                            "rehearsalOrderIndex",
                            t.optInt("orderIndex", j)
                        )
                    )
                )
            }
        }
    }
}
