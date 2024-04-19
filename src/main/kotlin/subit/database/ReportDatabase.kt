package subit.database

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import subit.dataClasses.*
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice

object ReportDatabase: DataAccessObject<ReportDatabase.Reports>(Reports)
{
    object Reports: IdTable<Long>("reports")
    {
        override val id = long("id").autoIncrement().entityId()
        val reportBy = reference("user", UserDatabase.Users)
        val objectType = enumerationByName("object_type", 16, ReportObject::class).index()
        val objectId = long("object_id").index()
        val reason = text("reason")
        val handledBy = reference("handled_by", UserDatabase.Users).nullable().index()
    }

    private fun deserialize(row: ResultRow) = Report(
        row[Reports.id].value,
        row[Reports.objectType],
        row[Reports.objectId],
        row[Reports.reportBy].value,
        row[Reports.reason]
    )

    suspend fun addReport(objectType: ReportObject, id: Long, user: UserId, reason: String) = query()
    {
        insert {
            it[this.objectType] = objectType
            it[this.objectId] = id
            it[this.reportBy] = user
            it[this.reason] = reason
        }
    }

    suspend fun getReport(id: Long): Report? = query()
    {
        Reports.select { Reports.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun handleReport(id: Long, user: UserId) = query()
    {
        Reports.update({ Reports.id eq id }) { it[handledBy] = user }
    }

    suspend fun getReports(
        begin: Long,
        count: Int,
        handled: Boolean?
    ):Slice<Report> = query()
    {
        val r = if (handled == null) Reports.selectAllBatched()
        else Reports.selectBatched {
            if (handled) (handledBy neq null)
            else (handledBy eq null)
        }
        r.flattenAsIterable().asSlice(begin, count).map(::deserialize)
    }
}