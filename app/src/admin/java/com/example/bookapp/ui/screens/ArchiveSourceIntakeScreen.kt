package com.example.bookapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.SourceDocumentIntake
import com.example.bookapp.data.AppDatabase
import com.example.bookapp.data.TaziehEntity
import com.example.bookapp.data.archive.TaziehVersionEntity
import com.example.bookapp.data.PdfPageRasterizer
import com.example.bookapp.data.SourceReviewWorkflow
import com.example.bookapp.data.SourceIntakePreview
import com.example.bookapp.data.SourceIntakeResult
import com.example.bookapp.domain.repository.ArchiveRepository
import kotlinx.coroutines.launch

/** Admin-only, review-first source intake screen. */
@Composable
fun ArchiveSourceIntakeScreen(
    repository: ArchiveRepository,
    db: AppDatabase,
    context: android.content.Context,
    onBack: () -> Unit
) {
    var versionUid by remember { mutableStateOf("") }
    var taziehs by remember { mutableStateOf(emptyList<TaziehEntity>()) }
    var selectedTazieh by remember { mutableStateOf<TaziehEntity?>(null) }
    var versions by remember { mutableStateOf(emptyList<TaziehVersionEntity>()) }
    var selectedVersion by remember { mutableStateOf<TaziehVersionEntity?>(null) }
    var taziehMenu by remember { mutableStateOf(false) }
    var versionMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SourceIntakePreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pdfPage by remember { mutableStateOf("1") }
    var renderedPagePath by remember { mutableStateOf<String?>(null) }
    var reviewedText by remember { mutableStateOf("") }
    var reviewerNote by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { taziehs = db.taziehDao().getAll() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = null
                result = null
                try {
                    when (val intake = SourceDocumentIntake(context, repository).intake(uri, versionUid)) {
                        is SourceIntakeResult.ReadyForReview -> {
                            result = intake.preview
                            reviewedText = intake.preview.extractedPreviewText.orEmpty()
                            message = "سند با موفقیت ثبت شد؛ وضعیت آن در انتظار بررسی انسانی است."
                        }
                        is SourceIntakeResult.Rejected -> message = intake.reason
                    }
                } catch (e: Exception) {
                    message = e.message ?: "ورود سند ناموفق بود."
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ورود سند اصلی تعزیه", style = MaterialTheme.typography.headlineSmall)
        Text(
            "فایل اصلی در حافظه خصوصی برنامه نگهداری می‌شود. متن استخراج‌شده فقط پیش‌نمایش است و تا تأیید شما وارد محتوای معتبر نمی‌شود.",
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            OutlinedButton(onClick = { taziehMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedTazieh?.title ?: "ابتدا تعزیه را انتخاب کنید") }
            DropdownMenu(expanded = taziehMenu, onDismissRequest = { taziehMenu = false }) {
                taziehs.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = {
                    selectedTazieh = item; selectedVersion = null; versionUid = ""; versions = emptyList(); taziehMenu = false
                    scope.launch { versions = db.archiveDao().versionsForTazieh(item.uid) }
                }) }
            }
        }
        Box {
            OutlinedButton(onClick = { versionMenu = true }, enabled = selectedTazieh != null, modifier = Modifier.fillMaxWidth()) { Text(selectedVersion?.let { "نسخه ${it.versionNumber}: ${it.title}" } ?: "انتخاب نسخه تعزیه") }
            DropdownMenu(expanded = versionMenu, onDismissRequest = { versionMenu = false }) {
                versions.forEach { item -> DropdownMenuItem(text = { Text("نسخه ${item.versionNumber}: ${item.title}${if (item.isCurrent) " (جاری)" else ""}") }, onClick = { selectedVersion = item; versionUid = item.uid; versionMenu = false }) }
            }
        }
        if (selectedVersion != null) Text("شناسه نسخه انتخاب‌شده: $versionUid", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { picker.launch(arrayOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/pdf"
            )) },
            enabled = !busy && versionUid.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("انتخاب فایل Word یا PDF") }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        result?.let { p ->
            Text("نتیجه بررسی اولیه", style = MaterialTheme.typography.titleMedium)
            Text("نام فایل: ${p.originalName}")
            Text("شناسه سند: ${p.sourceUid}")
            Text("SHA-256: ${p.sha256}")
            Text("حجم: ${p.byteCount} بایت")
            p.pageCount?.let { Text("تعداد صفحات PDF: $it") }
            if (p.mimeType.endsWith("wordprocessingml.document")) {
                Text("زمینه‌ها: ${p.detectedFields} | تعزیه‌ها: ${p.detectedTaziehs}")
                Text("نقش‌ها: ${p.detectedRoles} | بخش‌ها: ${p.detectedSections}")
                Text("پاورقی‌های استخراج‌شده برای تطبیق: ${p.extractedFootnotes}")
                Text("تصاویر استخراج‌شده: ${p.extractedMediaPaths.size}")
                Text("مسیر پیش‌نمایش: ${p.extractedPreviewPath ?: "ایجاد نشد"}")
            }
            if (p.mimeType == "application/pdf") {
                OutlinedTextField(value = pdfPage, onValueChange = { pdfPage = it.filter(Char::isDigit).take(4) }, label = { Text("شماره صفحه برای پیش‌نمایش OCR") }, singleLine = true)
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val page = (pdfPage.toIntOrNull() ?: 1) - 1
                            val output = java.io.File(context.cacheDir, "pdf-ocr-preview-${p.sourceUid}-$page.png")
                            renderedPagePath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                PdfPageRasterizer.renderPage(java.io.File(p.storedSourcePath), page, output).absolutePath
                            }
                            message = "تصویر صفحه برای ارسال به موتور OCR آماده شد؛ خود OCR به موتور فارسی/عربی نیاز دارد."
                        } catch (e: Exception) { message = e.message ?: "رندر صفحه ناموفق بود." }
                        finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("تبدیل صفحه انتخابی PDF به تصویر OCR") }
                renderedPagePath?.let { path ->
                    Text("تصویر صفحه آماده بررسی/OCR: $path", style = MaterialTheme.typography.bodySmall)
                    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
                    bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "پیش‌نمایش صفحه PDF", modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) }
                }
            }
            p.extractedPreviewText?.let {
                Text("پیش‌نمایش و بازبینی متن استخراج‌شده", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = reviewedText, onValueChange = { reviewedText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("متن استخراج‌شده؛ اصلاحات فقط در پرونده بازبینی ذخیره می‌شوند") })
                OutlinedTextField(value = reviewerNote, onValueChange = { reviewerNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("یادداشت پژوهشگر") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { require(SourceReviewWorkflow.verifyOriginal(java.io.File(p.storedSourcePath), p.sha256)) { "فایل اصلی با هش ثبت‌شده تطبیق ندارد." }; SourceReviewWorkflow.save(context, p.sourceUid, p.sha256, reviewedText, SourceReviewWorkflow.Status.PENDING, reviewerNote); message = "اصلاحات در پرونده بازبینی ذخیره شد؛ هنوز وارد متن قطعی نشده است." }
                            .onFailure { message = it.message ?: "ذخیره بازبینی ناموفق بود." }
                    }, modifier = Modifier.weight(1f)) { Text("ذخیره پیش‌نویس") }
                    Button(onClick = {
                        runCatching { require(SourceReviewWorkflow.verifyOriginal(java.io.File(p.storedSourcePath), p.sha256)) { "فایل اصلی با هش ثبت‌شده تطبیق ندارد." }; SourceReviewWorkflow.save(context, p.sourceUid, p.sha256, reviewedText, SourceReviewWorkflow.Status.ACCEPTED, reviewerNote); message = "پیش‌نمایش در پرونده بازبینی تأیید شد؛ ورود به متن اصلی همچنان نیازمند عملیات انتشار جداگانه است." }
                            .onFailure { message = it.message ?: "تأیید بازبینی ناموفق بود." }
                    }, modifier = Modifier.weight(1f)) { Text("تأیید بازبینی") }
                }
            }
            p.warnings.forEach { Text("هشدار: $it", color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
