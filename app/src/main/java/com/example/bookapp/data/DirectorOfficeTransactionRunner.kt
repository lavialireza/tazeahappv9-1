package com.example.bookapp.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/** مرز تراکنش مشترک برای دیتابیس مستقل هر Flavor. */
interface DirectorOfficeTransactionRunner {
    /** Runs a suspending transaction and returns the block result (for generated IDs/results). */
    suspend fun <T> run(block: suspend () -> T): T
}

/** فقط برای آزمون‌های واحد یا عملیات تک‌مرحله‌ای که تراکنش چندمرحله‌ای نمی‌خواهند. */
object DirectDirectorOfficeTransactionRunner : DirectorOfficeTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

/** اجرای عملیات مشترک داخل تراکنش Room دیتابیس Admin یا Viewer. */
class RoomDirectorOfficeTransactionRunner(
    private val database: RoomDatabase
) : DirectorOfficeTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}
