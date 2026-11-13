package com.example.bookapp.ui.screens

/**
 * A selectable section together with the exact archive revision it belongs to.
 * Defaults keep older call sites source-compatible until they are migrated.
 */
data class SectionPickerItem(
    val sectionId: Long,
    val roleTitle: String,
    val sectionTitle: String,
    val roleId: Long = 0L,
    val versionUid: String = "",
    val versionTitle: String = "",
    val versionNumber: Int? = null,
    val isCurrentVersion: Boolean = false
) {
    val versionLabel: String
        get() {
            val title = versionTitle.ifBlank {
                versionNumber?.let { "نسخه $it" } ?: "نسخه آرشیو"
            }
            val status = if (isCurrentVersion) "جاری" else "قدیمی"
            return "$title · $status"
        }
}
