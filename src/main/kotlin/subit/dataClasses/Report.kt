package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 举报信息
 * @property id 举报ID
 * @property objectType 举报对象类型
 * @property objectId 举报对象ID
 * @property user 举报用户ID
 * @property reason 举报原因
 */
@Serializable
data class Report(
    val id: ReportId,
    val objectType: ReportObject,
    val objectId: Long,
    val user: UserId,
    val reason: String
)
{
    companion object
    {
        val example = Report(
            ReportId(1),
            ReportObject.POST,
            1,
            UserId(1),
            "举报原因"
        )
    }
}

/**
 * 举报对象类型
 */
@Serializable
enum class ReportObject
{
    POST,
    COMMENT,
    USER,
    BLOCK
}