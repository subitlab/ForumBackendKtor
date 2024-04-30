package subit.database

import subit.dataClasses.Report
import subit.dataClasses.ReportObject
import subit.dataClasses.Slice
import subit.dataClasses.UserId

interface Reports
{
    suspend fun addReport(objectType: ReportObject, id: Long, user: UserId, reason: String)
    suspend fun getReport(id: Long): Report?
    suspend fun handleReport(id: Long, user: UserId)
    suspend fun getReports(
        begin: Long,
        count: Int,
        handled: Boolean?
    ): Slice<Report>
}