package com.example.bookapp.data

/**
 * قرارداد داده‌ای صفحه اصلی مشترک دفتر.
 * DAO مدیر و Viewer هر دو همین API را پیاده می‌کنند؛ تفاوت فقط دیتابیس تزریق‌شده است.
 * عملیات انتشار از این قرارداد حذف نشده، اما در صفحه مشترک فقط برای Admin فراخوانی می‌شود.
 */
interface DirectorOfficeMainPageDao : DirectorOfficeWorkspaceDao {
    suspend fun plansPage(query: String, limit: Int, offset: Int): List<DirectorPlanEntity>
    suspend fun plansForTazieh(taziehUid: String): List<DirectorPlanEntity>
    suspend fun insertPlan(value: DirectorPlanEntity): Long
    suspend fun versions(planId: Long): List<DirectorPlanVersionEntity>
    suspend fun insertVersion(value: DirectorPlanVersionEntity): Long
    suspend fun updateVersion(value: DirectorPlanVersionEntity)
    suspend fun sourceFiles(planId: Long): List<DirectorSourceFileEntity>
    suspend fun insertSourceFile(value: DirectorSourceFileEntity): Long
}
