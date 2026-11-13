package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RehearsalModeScreen(
    onIndividual: () -> Unit,
    onMultiRole: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("انتخاب نوع تمرین", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "ابتدا نوع تمرین خود را انتخاب کنید. در مرحله بعد می‌توانید زمینه، مجلس، نقش و بخش‌های موردنظر را تعیین کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModeCard(
                title = "تمرین انفرادی",
                description = "تمرین و حفظ متن یک نقش؛ با امکان انتخاب چند بخش از همان نقش.",
                icon = Icons.Filled.Person,
                background = Color(0xFFE8F1E7),
                foreground = Color(0xFF315B43),
                onClick = onIndividual
            )

            ModeCard(
                title = "تمرین چندنقشی",
                description = "انتخاب دو یا چند نقش، تعیین نقش اصلی و تمرین گفت‌وگوی میان نقش‌ها.",
                icon = Icons.Filled.Groups,
                background = Color(0xFFE9EEF8),
                foreground = Color(0xFF344C78),
                onClick = onMultiRole
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "در آینده می‌توانیم حالت‌های تمرینی دیگری را نیز به همین صفحه اضافه کنیم.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    background: Color,
    foreground: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(15.dp),
                color = foreground.copy(alpha = 0.12f)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = foreground)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = foreground)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.88f))
            }
        }
    }
}
