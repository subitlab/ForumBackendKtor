package subit.dataClasses

data class Report(val id: ReportId, val objectType: ReportObject, val objectId: Long, val user: UserId, val reason: String)

enum class ReportObject
{
    POST, COMMENT, USER, BLOCK
}