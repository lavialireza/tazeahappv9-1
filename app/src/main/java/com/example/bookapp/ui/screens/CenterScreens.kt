package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    randomVerse: SearchResult?,
    recentItems: List<SearchResult>,
    onOpenTaziehList: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenMyRole: () -> Unit,
    onOpenRehearsal: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenCompareGuide: () -> Unit,
    onItemClick: (SearchResult) -> Unit,
    showSearch: Boolean = true,
    showMyRole: Boolean = true,
    showRehearsal: Boolean = true,
    showGlossary: Boolean = true,
    showGallery: Boolean = true,
    showCompare: Boolean = true,
    onBack: () -> Unit
) {
    val ink = Color(0xFF263B5B)
    val muted = Color(0xFF647184)
    val green = Color(0xFFE5F0E2)
    val blue = Color(0xFFE5EDF5)
    val gold = Color(0xFFF4EBD7)
    val rose = Color(0xFFF4E7E6)
    val paper = Color(0xFFF7F7F2)
    
var librarySearchQuery by remember {
    mutableStateOf("")
}

val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Scaffold(
        containerColor = paper,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("کتابخانه تعزیه", fontWeight = FontWeight.Bold, color = ink)
                        Text("گنجینهٔ متن‌ها، نسخه‌ها و نقش‌ها", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = paper)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // معرفی کتابخانه و مسیر اصلی ورود به مجموعه
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5C3)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE9D6A8)) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(30.dp),
                                tint = Color(0xFF624515)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("کتابخانه تعزیه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ink)
                            Text("مطالعه، شناخت و بررسی میراث مکتوب تعزیه", style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                    }
                    Text(
                        "از زمینه و مجلس تا نسخه، نقش و متن؛ مجموعه را مرحله‌به‌مرحله دنبال کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink
                    )
                    Button(
                        onClick = onOpenTaziehList,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF624515), contentColor = Color.White)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        Text("ورود به فهرست زمینه‌ها و تعزیه‌ها", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                    }
                }
            }

  if (showSearch) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable {
                onOpenSearch("")
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F2E9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "جستجو",
                tint = ink
            )

            Spacer(Modifier.width(12.dp))

            Text(
                "جستجو در تعزیه‌ها، نقش‌ها و متن مجالس",
                color = muted,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )

            Icon(
                Icons.Filled.Tune,
                contentDescription = "جستجوی پیشرفته",
                tint = ink
            )
        }
    }
}   
            Text("ابزارهای کتابخانه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)

            if (showMyRole || showRehearsal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showMyRole) {
                        LibraryCompactCard("نقش من", "نقش‌های منتخب", Icons.Filled.Person, green, Color(0xFF28543D), onOpenMyRole, Modifier.weight(1f))
                    }
                    if (showRehearsal) {
                        LibraryCompactCard("حالت تمرین", "مرور و حفظ متن", Icons.Filled.School, blue, ink, onOpenRehearsal, Modifier.weight(1f))
                    }
                }
            }
            if (showGlossary || showGallery) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showGlossary) {
                        LibraryCompactCard("فرهنگ اصطلاحات", "واژه‌ها و معناها", Icons.AutoMirrored.Filled.MenuBook, gold, Color(0xFF735A2A), onOpenGlossary, Modifier.weight(1f))
                    }
                    if (showGallery) {
                        LibraryCompactCard("گالری تصاویر", "آثار و مستندات تصویری", Icons.Filled.PhotoLibrary, rose, Color(0xFF873F49), onOpenGallery, Modifier.weight(1f))
                    }
                }
            }
            if (showCompare) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCompareGuide),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9EAF1))
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = ink, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("مقایسه نقش‌ها و نسخه‌ها", fontWeight = FontWeight.Bold, color = ink)
                            Text("بررسی تفاوت‌ها برای مطالعه و پژوهش دقیق‌تر", style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = ink)
                    }
                }
            }

            if (recentItems.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ادامهٔ مطالعه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
                    Spacer(Modifier.weight(1f))
                    Text("${recentItems.size} مورد", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    recentItems.forEachIndexed { index, item ->
                        ListItem(
                            headlineContent = { Text(item.sectionTitle, fontWeight = FontWeight.SemiBold, color = ink) },
                            supportingContent = { Text("${item.taziehTitle}  ←  ${item.roleTitle}", color = muted) },
                            leadingContent = {
                                Surface(shape = RoundedCornerShape(11.dp), color = blue) {
                                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.padding(9.dp), tint = ink)
                                }
                            },
                            trailingContent = { Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = muted) },
                            modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (index != recentItems.lastIndex) HorizontalDivider(color = Color(0xFFEEF0F1))
                    }
                }
            }
            if (randomVerse != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onItemClick(randomVerse) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = gold)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(13.dp), color = Color(0xFFE9DDBE)) {
                            Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.padding(10.dp).size(25.dp), tint = Color(0xFF735A2A))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("برگی از گنجینه", style = MaterialTheme.typography.labelLarge, color = Color(0xFF735A2A), fontWeight = FontWeight.Bold)
                            Text(randomVerse.sectionTitle, style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.Bold)
                            Text("${randomVerse.taziehTitle}  ←  ${randomVerse.roleTitle}", style = MaterialTheme.typography.bodySmall, color = muted)
                        }
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = ink)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LibraryCompactCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(25.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = foreground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = foreground.copy(alpha = 0.82f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityCenterScreen(
    onOpenResearch: () -> Unit,
    onOpenExecution: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenTools: () -> Unit,
    showAdminSections: Boolean = true,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val entries = buildList {
        add(CenterEntry("مرکز پژوهش", "مقایسه نسخه‌ها، بررسی متن‌ها، دانشنامه و دستیار هوشمند پژوهش", Icons.Filled.School, Color(0xFFE8EDF8), onOpenResearch, "پژوهش"))
        add(CenterEntry("مرکز اجرا", "برنامه تمرین و اجرا، تخصیص نقش، گفت‌وگوی چندنقشی و اتاق فرمان", Icons.Filled.Event, Color(0xFFDCE9E4), onOpenExecution, "اجرا"))
        if (showAdminSections) {
            add(CenterEntry("مدیریت مستقل گروه", "گروه‌ها، اعضا، شبیه‌خوان‌ها و سوابق", Icons.Filled.People, Color(0xFFF4E5EC), onOpenGroups, "گروه"))
            add(CenterEntry("مرکز مدیریت و نگهداری محتوا", "ورود Word و JSON، خروجی، سلامت محتوا و پشتیبان‌گیری", Icons.Filled.Storage, Color(0xFFF8EBDD), onOpenManagement, "محتوا"))
        }
        add(CenterEntry("ابزار برنامه", "ابزارهای عمومی و امکانات کمکی برنامه", Icons.Filled.Build, Color(0xFFE8ECEE), onOpenTools, "ابزار"))
    }
    val filtered = entries.filter { query.isBlank() || (it.title + " " + it.subtitle + " " + it.keywords).contains(query.trim(), ignoreCase = true) }
    Scaffold(topBar = { TopAppBar(title = { Text("مرکز فعالیت‌ها") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("جستجوی بخش‌ها و امکانات") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
            filtered.forEach { entry ->
                Card(Modifier.fillMaxWidth().clickable(onClick = entry.onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = entry.tint)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(entry.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                    }
                }
            }
            if (filtered.isEmpty()) Text("بخشی با این عبارت پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchCenterScreen(
    showVersionComparison: Boolean,
    showAi: Boolean = true,
    showGlossary: Boolean = true,
    showEncyclopedia: Boolean = true,
    showCorrections: Boolean = true,
    onOpenAi: () -> Unit,
    onOpenVersionComparison: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenEncyclopedia: () -> Unit,
    onOpenCorrections: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("مرکز پژوهش") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ابزارهای بررسی، مستندسازی و تحلیل متون تعزیه", style = MaterialTheme.typography.bodyMedium)
            if (showAi) ResearchAction("دستیار هوش مصنوعی پژوهش", "تحلیل ساختار، پیشنهاد گوینده، استخراج واژه و جستجوی معنایی آفلاین در آرشیو", Icons.Filled.School, onOpenAi, Color(0xFFE8E1D2))
            if (showVersionComparison) ResearchAction("مقایسه نسخه‌های یک تعزیه", "بررسی اختلاف نسخه‌ها با حفظ متن و ترتیب منبع", Icons.Filled.Search, onOpenVersionComparison, Color(0xFFE8EDF8))
            if (showEncyclopedia) ResearchAction("دانشنامه تعزیه", "مدخل‌ها، شخصیت‌ها، رویدادها و پیوندهای دانشی", Icons.AutoMirrored.Filled.MenuBook, onOpenEncyclopedia, Color(0xFFDCE9E4))
            if (showGlossary) ResearchAction("دیکشنری و اصطلاحات", "واژه‌ها، صورت‌های نوشتاری و معنی‌های ثبت‌شده", Icons.AutoMirrored.Filled.MenuBook, onOpenGlossary, Color(0xFFF8EBDD))
            if (showCorrections) ResearchAction("ابزار تصحیح متن", "بررسی و مدیریت پیشنهادهای اصلاحی بدون تغییر خودکار متن اصلی", Icons.Filled.Edit, onOpenCorrections, Color(0xFFF4E5EC))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("اصل اعتبار پژوهش", fontWeight = FontWeight.Bold)
                    Text("پیشنهادهای هوشمند جایگزین منبع تاریخی یا تأیید پژوهشگر نیستند. متن اصلی و ترتیب مستند نسخه‌ها بدون اجازه ویرایش نمی‌شوند.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ResearchAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, tint: Color) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionCenterScreen(showAdminActions: Boolean = true, onOpenGroups: () -> Unit, onOpenExecutionManagement: () -> Unit, onOpenDirectorOffice: () -> Unit = {}, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("مرکز اجرا") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("مدیریت چرخه آماده‌سازی، تمرین و اجرای تعزیه", style = MaterialTheme.typography.bodyMedium)
            if (showAdminActions) {
                ResearchAction("مدیریت گروه‌ها و اعضا", "پرونده گروه، اعضا، شبیه‌خوان‌ها و سوابق", Icons.Filled.People, onOpenGroups, Color(0xFFDCE9E4))
                ResearchAction("برنامه تمرین و اجرا", "زمان‌بندی، تخصیص نقش، یادداشت کارگردان و متن اجرای زنده", Icons.Filled.Event, onOpenExecutionManagement, Color(0xFFE8EDF8))
                ResearchAction("دفتر معین البکا", "بارگذاری فایل نقش‌ها، تنظیم نوبت‌های جمعی و دستورهای صحنه", Icons.Filled.Description, onOpenDirectorOffice, Color(0xFFF4EBD7))
            } else {
                Text("امکانات مدیریتی مرکز اجرا در نسخه مدیر فعال هستند. امکانات مطالعه و تمرین متن از کتابخانه در دسترس‌اند.", style = MaterialTheme.typography.bodyMedium)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("پرونده اجرای مستند", fontWeight = FontWeight.Bold)
                    Text("هر اجرا باید به نسخه مشخصی از متن متصل باشد تا تغییرات بعدی نسخه، سوابق اجرای قبلی را تغییر ندهد.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementCenterScreen(onOpenContent: () -> Unit, onOpenAccess: () -> Unit, onOpenSpecialUsers: () -> Unit, onOpenAudit: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("مرکز مدیریت و نگهداری محتوا") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResearchAction("مدیریت محتوای اصلی", "ورود Word و JSON، خروجی‌ها، کنترل سلامت و پشتیبان‌گیری", Icons.Filled.Storage, onOpenContent, Color(0xFFF8EBDD))
            ResearchAction("مدیریت دسترسی Viewer", "تنظیم و انتقال سیاست‌های دسترسی نسخه عمومی", Icons.Filled.Storage, onOpenAccess, Color(0xFFE8EDF8))
            ResearchAction("مدیریت کاربران ویژه", "تعریف و مدیریت کاربران و مجوزهای اختصاصی", Icons.Filled.People, onOpenSpecialUsers, Color(0xFFF4E5EC))
            ResearchAction("سوابق تغییرات و دسترسی", "مشاهده گزارش رویدادها و تغییرات مدیریتی", Icons.Filled.History, onOpenAudit, Color(0xFFDCE9E4))
        }
    }
}

private data class CenterEntry(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit, val keywords: String)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsCenterScreen(onOpenCalendar: () -> Unit, onOpenVersion: () -> Unit, onOpenChangelog: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("ابزار برنامه") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResearchAction("تقویم محرم", "رویدادها و زمان‌شمار مناسبت‌های محرم", Icons.Filled.Event, onOpenCalendar, Color(0xFFE8E1D2))
            ResearchAction("نسخه برنامه", "اطلاعات نسخه نصب‌شده و وضعیت بروزرسانی", Icons.Filled.Info, onOpenVersion, Color(0xFFE8EDF8))
            ResearchAction("چه چیزی جدید است؟", "مشاهده تغییرات و امکانات نسخه‌ها", Icons.Filled.Info, onOpenChangelog, Color(0xFFDCE9E4))
        }
    }
}
