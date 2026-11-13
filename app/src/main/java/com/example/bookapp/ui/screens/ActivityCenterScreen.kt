package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** دسته‌بندی مرکزی امکانات؛ قابلیت‌های مدیریتی فقط در Admin نمایش داده می‌شوند. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityCenterScreen(
    isAdmin: Boolean,
    canRead: Boolean,
    canSearch: Boolean,
    canMyRole: Boolean,
    canDictionary: Boolean,
    canEncyclopedia: Boolean,
    canCorrections: Boolean,
    canAiResearch: Boolean,
    canCalendar: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMyRole: () -> Unit,
    onOpenGlossary: () -> Unit,
    onOpenEncyclopedia: () -> Unit,
    onOpenCorrections: () -> Unit,
    onOpenAiResearch: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenVersionComparison: () -> Unit,
    onOpenEncyclopediaManagement: () -> Unit,
    onOpenPerformanceManagement: () -> Unit,
    onOpenContentManagement: () -> Unit,
    onOpenViewerAccess: () -> Unit,
    onOpenSpecialUsers: () -> Unit,
    onOpenAccessAudit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMultiRoleDialogue: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مرکز فعالیت‌ها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "هر فعالیت در محیط تخصصی خودش؛ با دسترسی به اطلاعات مشترک کتابخانه تعزیه.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canRead || canSearch || canMyRole) item {
                CenterCategory("کتابخانه و مطالعه", "دسترسی به مجموعه، نقش‌ها و متن‌ها") {
                    if (canRead) CenterAction("کتابخانه تعزیه", Icons.AutoMirrored.Filled.MenuBook, onOpenLibrary)
                    if (canSearch) CenterAction("جستجوی سراسری", Icons.Filled.Search, onOpenSearch)
                    if (canMyRole) CenterAction("نقش من", Icons.Filled.Bookmarks, onOpenMyRole)
                }
            }
            if (canRead) item {
                CenterCategory("مرکز گفت‌وگو و تمرین", "ورود مستقل به گفت‌وگوهای چندنقشی هر تعزیه") {
                    CenterAction("گفت‌وگوی چندنقشی", Icons.Filled.Groups, onOpenMultiRoleDialogue)
                }
            }
            if (canDictionary || canEncyclopedia || canCorrections || canAiResearch || isAdmin) item {
                CenterCategory("مرکز پژوهش", "بررسی نسخه‌ها، منابع و دانش تعزیه") {
                    if (isAdmin) CenterAction("مقایسه نسخه‌های یک تعزیه", Icons.Filled.Science, onOpenVersionComparison)
                    if (canEncyclopedia) CenterAction("دانشنامه تعزیه", Icons.AutoMirrored.Filled.MenuBook, onOpenEncyclopedia)
                    if (isAdmin) CenterAction("مدیریت دانشنامه", Icons.Filled.Storage, onOpenEncyclopediaManagement)
                    if (canDictionary) CenterAction("دیکشنری اصطلاحات", Icons.AutoMirrored.Filled.MenuBook, onOpenGlossary)
                    if (canCorrections) CenterAction("اصلاح و تصحیح متن", Icons.AutoMirrored.Filled.MenuBook, onOpenCorrections)
                    if (canAiResearch) CenterAction("دستیار هوشمند پژوهش", Icons.Filled.Search, onOpenAiResearch)
                }
            }
            if (isAdmin) item {
                CenterCategory("مرکز اجرا", "مدیریت مستقل گروه، عوامل، تمرین و برنامه اجرا") {
                    CenterAction("مدیریت گروه‌ها و شبیه‌خوان‌ها", Icons.Filled.Groups, onOpenPerformanceManagement)
                }
            }
            if (isAdmin) item {
                CenterCategory("مرکز مدیریت", "مدیریت و نگهداری محتوای اصلی برنامه") {
                    CenterAction("مدیریت محتوا و اسناد", Icons.Filled.Storage, onOpenContentManagement)
                    CenterAction("مدیریت دسترسی Viewer", Icons.Filled.ManageAccounts, onOpenViewerAccess)
                    CenterAction("مدیریت کاربران", Icons.Filled.Groups, onOpenSpecialUsers)
                    CenterAction("سوابق تغییرات کاربران", Icons.Filled.Bookmarks, onOpenAccessAudit)
                }
            }
            item {
                CenterCategory("ابزارهای برنامه", "تنظیمات و ابزارهای عمومی") {
                    if (canCalendar) CenterAction("تقویم محرم", Icons.Filled.Bookmarks, onOpenCalendar)
                    CenterAction("تنظیمات", Icons.Filled.Settings, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun CenterCategory(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun CenterAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
