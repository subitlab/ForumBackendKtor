package subit.database

import subit.dataClasses.*

interface Reports
{
    suspend fun addReport(objectType: ReportObject, id: Long, user: UserId, reason: String)
    suspend fun getReport(id: ReportId): Report?
    suspend fun handleReport(id: ReportId, user: UserId)
    suspend fun getReports(
        begin: Long,
        count: Int,
        handled: Boolean?
    ): Slice<Report>
}