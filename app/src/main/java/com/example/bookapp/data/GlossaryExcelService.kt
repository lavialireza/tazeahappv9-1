package com.example.bookapp.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable Excel interchange for the glossary. The workbook is a genuine .xlsx file
 * (Office Open XML), generated without adding a runtime dependency to the offline app.
 * One worksheet row represents one meaning/application; repeated headwords are intentional.
 */
object GlossaryExcelService {
    private val headers = listOf(
        "واژه", "نوع واژه", "تلفظ", "ریشه‌شناسی", "یادداشت مدخل", "صورت‌های نوشتاری",
        "معنی", "عنوان منبع", "پدیدآورنده منبع", "صفحه/جلد", "نشانی منبع",
        "رشته", "مجلس", "نقش", "بخش", "شاهد کاربرد"
    )

    suspend fun export(db: AppDatabase): ByteArray {
        val dao = db.glossaryDao()
        val terms = dao.allTerms()
        val meanings = dao.allMeanings()
        val spellings = dao.allSpellings()
        val fields = db.fieldDao().getAll()
        val rows = mutableListOf<List<String>>()
        rows += headers
        terms.forEach { term ->
            val termSpellings = spellings.filter { it.termId == term.id }.joinToString("؛ ") { it.spelling }
            val termMeanings = meanings.filter { it.termId == term.id }
            if (termMeanings.isEmpty()) {
                rows += listOf(term.headword, term.partOfSpeech, term.pronunciation, term.etymology, term.notes, termSpellings) + List(10) { "" }
            } else termMeanings.forEach { meaning ->
                var fieldTitle = ""; var taziehTitle = ""; var roleTitle = ""; var sectionTitle = ""
                val sectionUid = meaning.usageSectionUid
                if (!sectionUid.isNullOrBlank()) {
                    val section = runCatching { db.sectionDao().getByUid(sectionUid) }.getOrNull()
                    if (section != null) {
                        sectionTitle = section.title
                        val role = runCatching { db.roleDao().getById(section.roleId) }.getOrNull()
                        if (role != null) {
                            roleTitle = role.title
                            val tazieh = runCatching { db.taziehDao().getById(role.taziehId) }.getOrNull()
                            if (tazieh != null) {
                                taziehTitle = tazieh.title
                                fieldTitle = fields.firstOrNull { it.id == tazieh.fieldId }?.title.orEmpty()
                            }
                        }
                    }
                }
                rows += listOf(
                    term.headword, term.partOfSpeech, term.pronunciation, term.etymology, term.notes, termSpellings,
                    meaning.meaning, meaning.sourceTitle, meaning.sourceAuthor, meaning.sourcePage, meaning.sourceUrl,
                    fieldTitle, taziehTitle, roleTitle, sectionTitle, meaning.usageQuote
                )
            }
        }
        return createWorkbook(rows)
    }

    /** Reads standard XLSX files using shared strings or inline strings. First row must contain glossary headers. */
    fun previewJson(input: InputStream, packageUid: String = "glossary-excel-${UUID.randomUUID()}"): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml")) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("برگه اول فایل Excel پیدا نشد یا فایل XLSX معتبر نیست.")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val matrix = parseSheet(sheet, shared)
        require(matrix.isNotEmpty()) { "فایل Excel خالی است." }
        val headerRow = matrix.first().map { it.trim() }
        val headerIndex = headers.associateWith { expected -> headerRow.indexOf(expected) }
        val missing = headers.filter { (headerIndex[it] ?: -1) < 0 }
        require(missing.isEmpty()) { "ستون‌های ضروری در فایل وجود ندارند: ${missing.joinToString("، ")}" }

        val terms = linkedMapOf<String, JSONObject>()
        matrix.drop(1).forEachIndexed { rowIndex, cells ->
            fun cell(name: String): String = cells.getOrNull(headerIndex[name] ?: -1).orEmpty().trim()
            val headword = cell("واژه")
            if (headword.isBlank()) return@forEachIndexed
            val termKey = normalize(headword)
            val term = terms.getOrPut(termKey) {
                JSONObject().put("headword", headword)
                    .put("partOfSpeech", cell("نوع واژه"))
                    .put("pronunciation", cell("تلفظ"))
                    .put("etymology", cell("ریشه‌شناسی"))
                    .put("notes", cell("یادداشت مدخل"))
                    .put("spellings", JSONArray())
                    .put("meanings", JSONArray())
            }
            val spellings = term.getJSONArray("spellings")
            cell("صورت‌های نوشتاری").split('؛').map { it.trim() }.filter { it.isNotBlank() }.forEach { spelling ->
                if ((0 until spellings.length()).none { normalize(spellings.optString(it)) == normalize(spelling) }) spellings.put(spelling)
            }
            val meaningText = cell("معنی")
            if (meaningText.isBlank()) return@forEachIndexed
            val sourceTitle = cell("عنوان منبع")
            val sourceUrl = cell("نشانی منبع")
            val fieldTitle = cell("رشته")
            val taziehTitle = cell("مجلس")
            val roleTitle = cell("نقش")
            val sectionTitle = cell("بخش")
            val meaning = JSONObject()
                .put("meaning", meaningText)
                .put("sourceTitle", sourceTitle)
                .put("sourceAuthor", cell("پدیدآورنده منبع"))
                .put("sourcePage", cell("صفحه/جلد"))
                .put("sourceUrl", sourceUrl)
                .put("usageFieldTitle", fieldTitle)
                .put("usageTaziehTitle", taziehTitle)
                .put("usageRoleTitle", roleTitle)
                .put("usageSectionTitle", sectionTitle)
                .put("usageQuote", cell("شاهد کاربرد"))
            term.getJSONArray("meanings").put(meaning)
        }
        val termArray = JSONArray()
        terms.values.forEach { termArray.put(term) }
        require(termArray.length() > 0) { "هیچ واژه‌ای در ردیف‌های فایل پیدا نشد." }
        return JSONObject().put("schemaVersion", 1).put("packageUid", packageUid).put("terms", termArray).toString()
    }

    private fun createWorkbook(rows: List<List<String>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="فرهنگ اصطلاحات" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val xml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed { rowIndex, row ->
                val rowNumber = rowIndex + 1
                xml.append("<row r=\"").append(rowNumber).append("\">")
                row.forEachIndexed { colIndex, value ->
                    val ref = columnName(colIndex + 1) + rowNumber
                    xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    xml.append(xmlEscape(value)).append("</t></is></c>")
                }
                xml.append("</row>")
            }
            xml.append("</sheetData></worksheet>")
            entry("xl/worksheets/sheet1.xml", xml.toString())
        }
        return output.toByteArray()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
        val result = mutableListOf<String>(); var inItem = false; var value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; value = StringBuilder() }
                    "t" -> if (inItem) value.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inItem) { result += value.toString(); inItem = false }
            }
            event = parser.eventType
            if (event != XmlPullParser.END_DOCUMENT) event = parser.next()
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
        val rows = mutableListOf<List<String>>()
        var row: MutableMap<Int, String>? = null
        var cellIndex = -1; var cellType = ""; var cellValue = StringBuilder(); var inCell = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        inCell = true; cellValue = StringBuilder(); cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellIndex = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                    }
                    "v", "t" -> if (inCell) cellValue.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val raw = cellValue.toString()
                        val value = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        if (cellIndex >= 0) row?.set(cellIndex, value)
                        inCell = false
                    }
                    "row" -> {
                        val current = row.orEmpty()
                        val max = current.keys.maxOrNull() ?: -1
                        rows += (0..max).map { current[it].orEmpty() }
                        row = null
                    }
                }
            }
            event = parser.eventType
            if (event != XmlPullParser.END_DOCUMENT) event = parser.next()
        }
        return rows
    }

    private fun columnName(number: Int): String {
        var n = number; val out = StringBuilder()
        while (n > 0) { val rem = (n - 1) % 26; out.insert(0, ('A'.code + rem).toChar()); n = (n - 1) / 26 }
        return out.toString()
    }

    private fun columnIndex(reference: String): Int {
        var result = 0
        for (char in reference.takeWhile { it.isLetter() }) result = result * 26 + (char.uppercaseChar().code - 'A'.code + 1)
        return result - 1
    }

    private fun xmlEscape(value: String): String = value.filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun normalize(value: String): String = value.trim().replace('ي', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه').replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("\\s+"), " ")
}
