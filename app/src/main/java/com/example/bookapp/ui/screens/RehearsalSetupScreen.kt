package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.FieldEntity
import com.example.bookapp.data.RoleEntity
import com.example.bookapp.data.SectionEntity

@Immutable
data class RehearsalRoleOption(
    val role: RoleEntity,
    val sections: List<SectionEntity>
)

@Immutable
data class RehearsalTaziehOption(
    val id: Long,
    val fieldId: Long,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RehearsalSetupScreen(
    fields: List<FieldEntity>,
    taziehs: List<RehearsalTaziehOption>,
    selectedFieldId: Long?,
    selectedTaziehId: Long?,
    roles: List<RehearsalRoleOption>,
    rolesLoading: Boolean,
    isMultiRoleMode: Boolean,
    onFieldSelected: (Long) -> Unit,
    onTaziehSelected: (Long?) -> Unit,
    onBack: () -> Unit,
    onStart: (
        taziehId: Long,
        primaryRoleId: Long,
        primaryRoleTitle: String,
        selectedSectionIds: List<Long>,
        multiRole: Boolean
    ) -> Unit
) {
    var showFieldPicker by remember { mutableStateOf(false) }
    var showTaziehPicker by remember { mutableStateOf(false) }
    var fieldQuery by remember { mutableStateOf("") }
    var taziehQuery by remember { mutableStateOf("") }
    var roleQuery by remember(selectedTaziehId) { mutableStateOf("") }

    val selectedField = fields.firstOrNull { it.id == selectedFieldId }
    val selectedTazieh = taziehs.firstOrNull { it.id == selectedTaziehId }
    val taziehsForField = remember(taziehs, selectedFieldId) {
        taziehs.filter { it.fieldId == selectedFieldId }
    }
    val visibleRoles = roles.filter { it.role.title.contains(roleQuery.trim(), ignoreCase = true) }

    val selectedSectionIds = remember(selectedTaziehId) { mutableStateListOf<Long>() }
    val expandedRoleIds = remember(selectedTaziehId) { mutableStateListOf<Long>() }
    var primaryRoleId by remember(selectedTaziehId) { mutableStateOf<Long?>(null) }

    val selectedRoleIdsInOrder = selectedSectionIds.mapNotNull { sectionId ->
        roles.firstOrNull { option -> option.sections.any { it.id == sectionId } }?.role?.id
    }.distinct()
    val selectedRoles = selectedRoleIdsInOrder.mapNotNull { id ->
        roles.firstOrNull { it.role.id == id }
    }
    val selectedRoleCount = selectedRoles.size
    val selectedCount = selectedSectionIds.size

    fun toggleSection(roleId: Long, sectionId: Long, checked: Boolean) {
        if (checked) {
            if (!isMultiRoleMode) {
                val alreadySelectedRoleIds = selectedSectionIds.mapNotNull { selectedId ->
                    roles.firstOrNull { option -> option.sections.any { it.id == selectedId } }?.role?.id
                }.distinct()
                if (alreadySelectedRoleIds.any { it != roleId }) {
                    selectedSectionIds.clear()
                }
                primaryRoleId = roleId
            }
            if (sectionId !in selectedSectionIds) selectedSectionIds.add(sectionId)
            if (primaryRoleId == null || primaryRoleId !in selectedRoleIdsInOrder) {
                primaryRoleId = roleId
            }
        } else {
            selectedSectionIds.remove(sectionId)
            val stillSelected = selectedSectionIds.any { id ->
                roles.firstOrNull { option -> option.role.id == roleId }
                    ?.sections?.any { it.id == id } == true
            }
            if (!stillSelected && primaryRoleId == roleId) {
                val nextRole = selectedSectionIds.firstNotNullOfOrNull { id ->
                    roles.firstOrNull { option -> option.sections.any { it.id == id } }?.role?.id
                }
                primaryRoleId = nextRole
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isMultiRoleMode) "تنظیم تمرین چندنقشی" else "تنظیم تمرین انفرادی", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        when {
                            selectedCount == 0 -> "هنوز بخشی انتخاب نشده است"
                            isMultiRoleMode && selectedRoleCount < 2 -> "برای تمرین چندنقشی، حداقل دو نقش انتخاب کنید."
                            isMultiRoleMode -> "$selectedCount بخش از $selectedRoleCount نقش انتخاب شده است."
                            else -> "$selectedCount بخش از یک نقش برای تمرین انفرادی انتخاب شده است."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val tazieh = selectedTazieh ?: return@Button
                            val chosenRoleIds = selectedRoles.map { it.role.id }
                            val primaryId = primaryRoleId?.takeIf { it in chosenRoleIds }
                                ?: chosenRoleIds.firstOrNull()
                                ?: return@Button
                            val primaryTitle = roles.firstOrNull { it.role.id == primaryId }
                                ?.role?.title.orEmpty()
                            onStart(
                                tazieh.id,
                                primaryId,
                                primaryTitle,
                                selectedSectionIds.toList(),
                                isMultiRoleMode
                            )
                        },
                        enabled = selectedTazieh != null && selectedCount > 0 &&
                            (if (isMultiRoleMode) selectedRoleCount >= 2 else selectedRoleCount == 1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isMultiRoleMode) "شروع تمرین چندنقشی" else "شروع تمرین انفرادی")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("۱. انتخاب زمینه", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { showFieldPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedField?.title ?: "ابتدا زمینه را انتخاب کنید", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("۲. انتخاب مجلس تعزیه", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { if (selectedFieldId != null) showTaziehPicker = true },
                            enabled = selectedFieldId != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(selectedTazieh?.title ?: "پس از انتخاب زمینه، مجلس را انتخاب کنید", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        Text(
                            when {
                                selectedFieldId == null -> "انتخاب تعزیه پس از تعیین زمینه فعال می‌شود."
                                selectedTazieh == null -> "${taziehsForField.size} مجلس در این زمینه وجود دارد. برای یافتن سریع‌تر جستجو کنید."
                                else -> "اکنون نقش‌ها و بخش‌های همین مجلس نمایش داده می‌شوند."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (selectedTazieh != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("۳. انتخاب نقش و بخش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (isMultiRoleMode)
                                "دو یا چند نقش را انتخاب کنید. اولین نقش انتخاب‌شده به‌صورت پیش‌فرض نقش اصلی است؛ در صورت نیاز می‌توانید آن را تغییر دهید."
                            else
                                "در این حالت فقط یک نقش انتخاب می‌شود؛ می‌توانید چند بخش از همان نقش را برای تمرین انتخاب کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = roleQuery,
                        onValueChange = { roleQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("جستجوی نقش در این مجلس") }
                    )
                }

                if (rolesLoading) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (roles.isEmpty()) {
                    item { Text("برای این مجلس هنوز نقشی ثبت نشده است.", modifier = Modifier.padding(12.dp)) }
                } else if (visibleRoles.isEmpty()) {
                    item { Text("نقشی با این عنوان پیدا نشد.", modifier = Modifier.padding(12.dp)) }
                } else {
                    items(visibleRoles, key = { it.role.id }) { option ->
                        val roleId = option.role.id
                        val selectedInRole = option.sections.count { it.id in selectedSectionIds }
                        val roleIsSelected = selectedInRole > 0
                        val expanded = roleId in expandedRoleIds
                        val isPrimary = primaryRoleId == roleId && roleIsSelected

                        Card(shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier.weight(1f).clickable {
                                            if (expanded) expandedRoleIds.remove(roleId) else expandedRoleIds.add(roleId)
                                        }.padding(vertical = 5.dp)
                                    ) {
                                        Text(option.role.title, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (option.sections.isEmpty()) "بخشی برای تمرین ندارد"
                                            else "$selectedInRole از ${option.sections.size} بخش انتخاب شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (roleIsSelected && isMultiRoleMode) {
                                        if (isPrimary) {
                                            AssistChip(onClick = {}, label = { Text("نقش اصلی") }, enabled = false)
                                        } else {
                                            TextButton(onClick = { primaryRoleId = roleId }) { Text("نقش اصلی") }
                                        }
                                    }
                                    IconButton(onClick = {
                                        if (expanded) expandedRoleIds.remove(roleId) else expandedRoleIds.add(roleId)
                                    }) {
                                        Icon(
                                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (expanded) "بستن بخش‌ها" else "نمایش بخش‌ها"
                                        )
                                    }
                                }

                                if (expanded) {
                                    if (option.sections.isEmpty()) {
                                        Text("متنی برای این نقش ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        option.sections.forEach { section ->
                                            HorizontalDivider()
                                            val checked = section.id in selectedSectionIds
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = { toggleSection(roleId, section.id, it) }
                                                )
                                                Text(
                                                    section.title,
                                                    modifier = Modifier.weight(1f).clickable {
                                                        toggleSection(roleId, section.id, !checked)
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFieldPicker) {
        val filteredFields = fields.filter { it.title.contains(fieldQuery.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showFieldPicker = false },
            title = { Text("انتخاب زمینه") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fieldQuery,
                        onValueChange = { fieldQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("جستجوی زمینه") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filteredFields, key = { it.id }) { field ->
                            ListItem(
                                headlineContent = { Text(field.title) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onFieldSelected(field.id)
                                    onTaziehSelected(null)
                                    showFieldPicker = false
                                    fieldQuery = ""
                                    taziehQuery = ""
                                }
                            )
                        }
                        if (filteredFields.isEmpty()) {
                            item { Text("زمینه‌ای با این عنوان پیدا نشد.", modifier = Modifier.padding(12.dp)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFieldPicker = false }) { Text("بستن") } }
        )
    }

    if (showTaziehPicker) {
        val filteredTaziehs = taziehsForField.filter {
            it.title.contains(taziehQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showTaziehPicker = false },
            title = { Text("انتخاب مجلس تعزیه") },
            text = {
                Column {
                    OutlinedTextField(
                        value = taziehQuery,
                        onValueChange = { taziehQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("جستجوی مجلس در ${selectedField?.title.orEmpty()}") }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(filteredTaziehs, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.title) },
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onTaziehSelected(item.id)
                                    showTaziehPicker = false
                                    taziehQuery = ""
                                }
                            )
                        }
                        if (filteredTaziehs.isEmpty()) {
                            item { Text("مجلس تعزیه‌ای با این عنوان پیدا نشد.", modifier = Modifier.padding(12.dp)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTaziehPicker = false }) { Text("بستن") } }
        )
    }
}
