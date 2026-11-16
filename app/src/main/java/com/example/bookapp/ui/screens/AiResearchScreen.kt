package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.AiResearchEngine
import com.example.bookapp.data.SearchCorpusRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiResearchScreen(
    corpus: List<SearchCorpusRow>,
    canAnalyze: Boolean,
    canSpeaker: Boolean,
    canGlossary: Boolean,
    canSemanticSearch: Boolean,
    canTranscribe: Boolean,
    onOpenSection: (Long) -> Unit,
    onBack: () -> Unit
) {
    var sourceText by remember { mutableStateOf("") }
    var semanticQuery by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf(emptyList<AiResearchEngine.Finding>()) }
    var semanticHits by remember { mutableStateOf(emptyList<AiResearchEngine.SemanticHit>()) }
    var activeTool by remember { mutableStateOf("راهنما") }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!recognized.isNullOrBlank()) sourceText = listOf(sourceText, recognized).filter { it.isNotBlank() }.joinToString("\n")
    }
    Scaffold(topBar = { TopAppBar(title = { Text("دستیار هوشمند پژوهش") }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("پردازش آفلاین و کمکی", style = MaterialTheme.typography.titleMedium)
                        Text("این ابزارها روی دستگاه اجرا می‌شوند، متن اصلی را تغییر نمی‌دهند و نتیجه‌ها صرفاً پیشنهاد پژوهشی هستند. برای متن‌های تاریخی، تأیید انسانی ضروری است.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedTextField(value = sourceText, onValueChange = { sourceText = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("متن تعزیه / گفتار برای تحلیل") })
            }
            item {
                Text("ابزارهای تحلیل متن", style = MaterialTheme.typography.titleMedium)
                if (canAnalyze) Button(onClick = { findings = AiResearchEngine.analyzeStructure(sourceText); activeTool = "تحلیل ساختار" }, modifier = Modifier.fillMaxWidth(), enabled = sourceText.isNotBlank()) { Text("تحلیل ساختار، عنوان‌ها و تکرارها") }
                if (canSpeaker) OutlinedButton(onClick = { findings = AiResearchEngine.suggestSpeakers(sourceText); activeTool = "گوینده‌ها" }, modifier = Modifier.fillMaxWidth(), enabled = sourceText.isNotBlank()) { Text("پیشنهاد تفکیک گوینده‌ها") }
                if (canGlossary) OutlinedButton(onClick = { findings = AiResearchEngine.glossaryCandidates(sourceText); activeTool = "واژه‌ها" }, modifier = Modifier.fillMaxWidth(), enabled = sourceText.isNotBlank()) { Text("استخراج واژه‌های پرتکرار") }
                if (canTranscribe) OutlinedButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "گفتار تعزیه را بیان کنید")
                    }
                    runCatching { speechLauncher.launch(intent) }
                }, modifier = Modifier.fillMaxWidth()) { Text("تبدیل گفتار به متن با سرویس دستگاه") }
                if (canTranscribe) Text("تشخیص گفتار توسط سرویس نصب‌شده روی دستگاه انجام می‌شود و ممکن است بسته به سرویس، اتصال اینترنت لازم باشد.", style = MaterialTheme.typography.bodySmall)
                if (!canAnalyze && !canSpeaker && !canGlossary) Text("مدیر دسترسی ابزارهای تحلیل متن را برای این کاربر فعال نکرده است.", color = MaterialTheme.colorScheme.error)
            }
            if (findings.isNotEmpty()) {
                item { Text("نتایج: $activeTool", style = MaterialTheme.typography.titleMedium) }
                items(findings) { finding ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(finding.title, style = MaterialTheme.typography.titleSmall)
                            Text(finding.detail, style = MaterialTheme.typography.bodyMedium)
                            if (finding.sample.isNotBlank()) Text(finding.sample, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text("جستجوی معنایی آفلاین در آرشیو", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = semanticQuery, onValueChange = { semanticQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("مفهوم یا عبارت موردنظر") })
                Button(onClick = { semanticHits = AiResearchEngine.semanticSearch(corpus, semanticQuery); activeTool = "جستجوی معنایی" }, enabled = canSemanticSearch && semanticQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("جستجو در ${corpus.size} بخش") }
                if (!canSemanticSearch) Text("مدیر دسترسی جستجوی معنایی را برای این کاربر فعال نکرده است.", color = MaterialTheme.colorScheme.error)
            }
            items(semanticHits) { hit ->
                Card(Modifier.fillMaxWidth().clickable { onOpenSection(hit.row.sectionId) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hit.row.taziehTitle, style = MaterialTheme.typography.titleSmall)
                        Text("${hit.row.roleTitle} ← ${hit.row.sectionTitle}", style = MaterialTheme.typography.bodyMedium)
                        Text("امتیاز تطبیق: ${hit.score}", style = MaterialTheme.typography.labelSmall)
                        Text(hit.snippet, style = MaterialTheme.typography.bodySmall)
                        Text("بازکردن متن", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (semanticHits.isEmpty() && semanticQuery.isNotBlank() && activeTool == "جستجوی معنایی") item { Text("نتیجه‌ای پیدا نشد.") }
        }
    }
}
