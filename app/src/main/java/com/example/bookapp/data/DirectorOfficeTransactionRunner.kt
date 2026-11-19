package com.example.bookapp.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** مرز تراکنش مشترک برای دیتابیس مستقل هر Flavor. */
interface DirectorOfficeTransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

/** فقط برای آزمون‌های واحد یا عملیات تک‌مرحله‌ای که تراکنش چندمرحله‌ای نمی‌خواهند. */
object DirectDirectorOfficeTransactionRunner : DirectorOfficeTransactionRunner {
    override suspend fun run(block: suspend () -> Unit) {
        block()
    }
}

/** اجرای عملیات مشترک داخل تراکنش Room دیتابیس Admin یا Viewer. */
class RoomDirectorOfficeTransactionRunner(
    private val database: RoomDatabase
) : DirectorOfficeTransactionRunner {
    override suspend fun run(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }
}
