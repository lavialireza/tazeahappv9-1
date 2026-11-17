package com.example.bookapp.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import android.content.Context
import com.example.bookapp.data.AppDatabase
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

fun viewerDirectorOfficeRoutes(
    navController: NavHostController,
    context: Context,
    db: AppDatabase,
    onBack: () -> Unit
) {
    composable("viewer_director_office") {
        ViewerDirectorOfficeScreen(onBack = onBack)
    }
    composable("viewer_director_office/{taziehId}") { entry ->
        val id = entry.arguments?.getString("taziehId")?.toLongOrNull()
        var uid by remember(id) { mutableStateOf<String?>(null) }
        var title by remember(id) { mutableStateOf("") }
        LaunchedEffect(id) {
            if (id != null) {
                val tazieh = db.taziehDao().getById(id)
                uid = tazieh?.uid
                title = tazieh?.title.orEmpty()
            }
        }
        ViewerDirectorOfficeScreen(taziehUid = uid, taziehTitle = title, onBack = onBack)
    }
}
