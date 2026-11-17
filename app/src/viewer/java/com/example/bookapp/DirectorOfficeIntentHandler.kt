package com.example.bookapp

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookapp.data.DirectorOfficePublicationContract
import com.example.bookapp.data.ViewerDirectorOfficeDatabase
import com.example.bookapp.data.ViewerDirectorOfficePublicationImporter
import kotlinx.coroutines.launch

/** دریافت مستقیم بسته رسمی از Share Sheet؛ فقط در flavor Viewer وجود دارد. */
fun handleDirectorOfficePublicationIntent(activity: ComponentActivity, incoming: Intent?): Boolean {
    if (incoming?.action != Intent.ACTION_SEND || incoming.type != DirectorOfficePublicationContract.MIME_TYPE) return false
    val uri = incoming.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
    if (uri == null) {
        android.widget.Toast.makeText(activity, "فایل دفتر رسمی در پیام وجود ندارد.", android.widget.Toast.LENGTH_LONG).show()
        return true
    }
    activity.lifecycleScope.launch {
        try {
            val result = ViewerDirectorOfficePublicationImporter.import(
                activity, uri, ViewerDirectorOfficeDatabase.official(activity)
            )
            android.widget.Toast.makeText(activity, result, android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(activity, "دریافت دفتر رسمی ناموفق بود: ${e.message ?: "فایل نامعتبر است."}", android.widget.Toast.LENGTH_LONG).show()
        } finally {
            activity.setIntent(Intent())
        }
    }
    return true
}
