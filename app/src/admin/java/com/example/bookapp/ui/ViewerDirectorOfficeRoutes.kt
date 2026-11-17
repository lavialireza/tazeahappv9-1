package com.example.bookapp.ui

import android.content.Context
import androidx.navigation.NavHostController
import com.example.bookapp.data.AppDatabase

/** The Viewer-only director-office UI is deliberately absent from the Admin source set. */
fun viewerDirectorOfficeRoutes(
    navController: NavHostController,
    context: Context,
    db: AppDatabase,
    onBack: () -> Unit
) {
    // No Viewer routes are registered in the Admin APK.
}
