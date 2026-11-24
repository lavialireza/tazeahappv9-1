package com.example.bookapp.data

import android.content.Context

/**
 * موتور مرکزی ارزیابی دسترسی Viewer.
 * تمام بررسی‌های والد/فرزند از یک نقطه انجام می‌شوند تا UI و عملیات واقعی
 * در صورت خاموش شدن یک مجوز، رفتار یکسان داشته باشند.
 */
object ViewerPermissionEngine {
    fun permissions(context: Context): Map<String, Boolean> =
        ViewerAccessPolicy.getEffectivePermissions(context)

    fun can(context: Context, key: String): Boolean =
        can(permissions(context), key)

    /** Fast path for Compose: evaluate a previously loaded immutable policy snapshot. */
    fun can(permissions: Map<String, Boolean>, key: String): Boolean {
        if (key !in ViewerAccessPolicy.permissionLabels) return false
        val parent = ViewerAccessPolicy.permissionParents[key]
        return permissions[key] == true && (parent == null || permissions[parent] == true)
    }

    fun canAny(context: Context, vararg keys: String): Boolean =
        canAny(permissions(context), *keys)

    fun canAny(permissions: Map<String, Boolean>, vararg keys: String): Boolean =
        keys.any { can(permissions, it) }

    fun canAll(context: Context, vararg keys: String): Boolean =
        canAll(permissions(context), *keys)

    fun canAll(permissions: Map<String, Boolean>, vararg keys: String): Boolean =
        keys.all { can(permissions, it) }

    fun parentOf(key: String): String? =
        ViewerAccessPolicy.permissionParents[key]
}
