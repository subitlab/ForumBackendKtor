package subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
data class Prohibit(
    val user: UserId,
    val time: Long,
    val reason: String,
    val operator: UserId,
)