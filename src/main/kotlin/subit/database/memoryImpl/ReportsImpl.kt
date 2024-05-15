package subit.database.memoryImpl

import subit.dataClasses.*
import subit.dataClasses.ReportId.Companion.toReportId
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.Reports
import java.util.*

class ReportsImpl: Reports
{
    private val unHandled = Collections.synchronizedMap(hashMapOf<ReportId, Report>())
    private val handled = Collections.synchronizedMap(hashMapOf<ReportId, Pair<Report, UserId>>())

    override suspend fun addReport(objectType: ReportObject, id: Long, user: UserId, reason: String)
    {
        val rid = (unHandled.size+1).toReportId()
        unHandled[rid] = Report(
            id = rid,
            objectType = objectType,
            objectId = id,
            user = user,
            reason = reason
        )
    }
    override suspend fun getReport(id: ReportId): Report? = unHandled[id]
    override suspend fun handleReport(id: ReportId, user: UserId)
    {
        val report = unHandled.remove(id) ?: return
        handled[id] = report to user
    }
    override suspend fun getReports(begin: Long, count: Int, handled: Boolean?): Slice<Report>
    {
        if (handled == true) return this.handled.values.map { it.first }.asSlice(begin, count)
        if (handled == false) return unHandled.values.asSlice(begin, count)
        return (unHandled.values + this.handled.values.map { it.first }).asSlice(begin, count)
    }
}