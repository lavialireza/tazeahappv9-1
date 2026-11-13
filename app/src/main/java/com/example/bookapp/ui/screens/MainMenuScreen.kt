
package com.example.bookapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke

// -----------------------------------------------------
// تنظیمات قابل تغییر صفحه اصلی
// -----------------------------------------------------

private const val SHOW_UTILITY_HEADING = false
private const val UTILITY_HEADING = "ابزارهای کاربردی"

// -----------------------------------------------------
// رنگ‌بندی صفحه اصلی
// -----------------------------------------------------

private val HomeBackground = Color(0xFFF5F5EF)

private val GoldBackground = Color(0xFFF3E5C3)
private val GoldDark = Color(0xFF624515)

private val GreenBackground = Color(0xFFE1EEDC)
private val GreenDark = Color(0xFF174D35)

private val BlueBackground = Color(0xFFE0EAF3)
private val BlueDark = Color(0xFF173E5B)

private val RoseBackground = Color(0xFFF3E0E0)
private val RoseDark = Color(0xFF873333)

private val NavyBackground = Color(0xFFE5E8F1)
private val NavyDark = Color(0xFF263B5B)

private val GoldBorder = Color(0xFFB99A58)
private val GreenBorder = Color(0xFF7C9D76)
private val BlueBorder = Color(0xFF829BB5)

// -----------------------------------------------------
// صفحه اصلی
// -----------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onOpenLibrary: () -> Unit,
    onOpenActivityCenter: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onExit: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }

    val smallItems = listOf(
        HomeMenuItem(
            title = "دفتر یادداشت",
            icon = Icons.Filled.Edit,
            background = GreenBackground,
            foreground = GreenDark,
            onClick = onOpenNotes
        ),
        HomeMenuItem(
            title = "جستجو",
            icon = Icons.Filled.Search,
            background = BlueBackground,
            foreground = BlueDark,
            onClick = onOpenSearch
        ),
        HomeMenuItem(
            title = "علاقه‌مندی‌ها",
            icon = Icons.Filled.Favorite,
            background = RoseBackground,
            foreground = RoseDark,
            onClick = onOpenBookmarks
        ),
        HomeMenuItem(
            title = "تنظیمات",
            icon = Icons.Filled.Settings,
            background = NavyBackground,
            foreground = NavyDark,
            onClick = onOpenSettings
        )
    )

    Scaffold(
        containerColor = HomeBackground,

      

topBar = {
    Surface(
        color = HomeBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            TaziehLogo()

            Spacer(modifier = Modifier.width(13.dp))

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "تعزیه و شبیه‌خوانی",
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "میراثی برای شناخت، پژوهش و ماندگاری",
                    color = BlueDark,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
},


bottomBar = {
    Surface(
        color = HomeBackground,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 5.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(
                onClick = { showExitDialog = true },
                modifier = Modifier
                    .width(190.dp)
                    .height(44.dp),
                shape = CircleShape,
                border = BorderStroke(1.6.dp, RoseDark),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RoseDark
                ),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 6.dp
                )
            ) {
                Text(
                    text = "خروج از برنامه",
                    color = RoseDark,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // کارت اول: کتابخانه
            HomeFeatureCard(
                title = "کتابخانه تعزیه",
                subtitle = "مجموعه تعزیه‌ها، نسخه‌ها و متن مجالس",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                background = GoldBackground,
                foreground = GoldDark,
                borderColor = GoldBorder,
                onClick = onOpenLibrary
            )

            // کارت دوم: مرکز فعالیت‌ها
            HomeFeatureCard(
                title = "مرکز فعالیت‌ها",
                subtitle = "پژوهش، اجرا، مدیریت گروه و فعالیت‌های تخصصی",
                icon = Icons.Filled.Apps,
                background = GreenBackground,
                foreground = GreenDark,
                borderColor = GreenBorder,
                onClick = onOpenActivityCenter
            )

            // کارت سوم: درباره برنامه
            HomeFeatureCard(
                title = "درباره برنامه",
                subtitle = "معرفی سامانه، اهداف و اطلاعات برنامه",
                icon = Icons.Filled.Info,
                background = BlueBackground,
                foreground = BlueDark,
                borderColor = BlueBorder,
                onClick = onOpenAbout
            )

            // عنوان ابزارها حفظ شده و با یک تنظیم قابل فعال‌سازی است.
            if (SHOW_UTILITY_HEADING) {
                Text(
                    text = UTILITY_HEADING,
                    color = NavyDark,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        top = 3.dp,
                        bottom = 1.dp,
                        start = 4.dp
                    )
                )
            }

            // ردیف اول ابزارها
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                HomeSmallCard(
                    item = smallItems[0],
                    modifier = Modifier.weight(1f)
                )

                HomeSmallCard(
                    item = smallItems[1],
                    modifier = Modifier.weight(1f)
                )
            }

            // ردیف دوم ابزارها
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                HomeSmallCard(
                    item = smallItems[2],
                    modifier = Modifier.weight(1f)
                )

                HomeSmallCard(
                    item = smallItems[3],
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // پنجره تأیید خروج
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
            },

            title = {
                Text(
                    text = "خروج از برنامه",
                    color = NavyDark,
                    fontWeight = FontWeight.Bold
                )
            },

            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید از برنامه خارج شوید؟"
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExit()
                    }
                ) {
                    Text(
                        text = "بله، خروج",
                        color = RoseDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                    }
                ) {
                    Text(
                        text = "انصراف",
                        color = NavyDark
                    )
                }
            },

            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }
}

// -----------------------------------------------------
// لوگوی موقت تعزیه و شبیه‌خوانی
// -----------------------------------------------------

@Composable
private fun TaziehLogo() {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = Color(0xFFF2E7CB),
        border = BorderStroke(1.dp, GoldBorder),
        shadowElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "لوگوی تعزیه و شبیه‌خوانی",
                tint = NavyDark,
                modifier = Modifier.size(34.dp)
            )

            // میله و پرچم قرمز کوچک
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val poleX = size.width * 0.76f
                val poleTop = size.height * 0.08f
                val poleBottom = size.height * 0.48f

                drawLine(
                    color = GoldDark,
                    start = Offset(poleX, poleTop),
                    end = Offset(poleX, poleBottom),
                    strokeWidth = 2.5f
                )

                val flag = Path().apply {
                    moveTo(poleX, poleTop)
                    lineTo(size.width * 0.96f, size.height * 0.16f)
                    lineTo(size.width * 0.84f, size.height * 0.25f)
                    lineTo(poleX, size.height * 0.22f)
                    close()
                }

                drawPath(
                    path = flag,
                    color = Color(0xFFB83232)
                )
            }
        }
    }
}

// -----------------------------------------------------
// اطلاعات کارت‌ها
// -----------------------------------------------------

private data class HomeMenuItem(
    val title: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color,
    val onClick: () -> Unit
)

// -----------------------------------------------------
// کارت‌های بزرگ
// -----------------------------------------------------

@Composable
private fun HomeFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(onClick = onClick),

        shape = RoundedCornerShape(19.dp),

        colors = CardDefaults.cardColors(
            containerColor = background
        ),

        border = BorderStroke(
            0.8.dp,
            borderColor.copy(alpha = 0.55f)
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.5.dp,
            pressedElevation = 3.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 12.dp),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.88f),
                border = BorderStroke(
                    0.6.dp,
                    foreground.copy(alpha = 0.12f)
                ),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = foreground,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = foreground,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = subtitle,
                    color = foreground.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// -----------------------------------------------------
// کارت‌های کوچک
// -----------------------------------------------------
@Composable
private fun HomeSmallCard(
    item: HomeMenuItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 94.dp)
            .clickable(onClick = item.onClick),

        shape = RoundedCornerShape(18.dp),

        colors = CardDefaults.cardColors(
            containerColor = item.background
        ),

        border = BorderStroke(
            0.8.dp,
            item.foreground.copy(alpha = 0.22f)
        ),

        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.5.dp,
            pressedElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),

            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.88f),
                border = BorderStroke(
                    0.6.dp,
                    item.foreground.copy(alpha = 0.12f)
                ),
                modifier = Modifier.size(35.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.foreground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = item.title,
                color = item.foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}
