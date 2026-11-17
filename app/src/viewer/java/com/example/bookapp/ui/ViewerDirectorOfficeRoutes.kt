package com.example.bookapp.ui

import android.content.Context
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.bookapp.data.AppDatabase

fun NavGraphBuilder.viewerOnlyRoutes(
    navController: NavHostController,
    context: Context,
    db: AppDatabase
) {
    composable("viewer_director_office") {
        ViewerDirectorOfficeScreen(contentDb = db, onBack = { navController.popBackStack() })
    }
}
