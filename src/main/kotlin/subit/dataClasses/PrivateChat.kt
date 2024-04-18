package subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
data class PrivateChat(
    val from: UserId,
    val to: UserId,
    val time: Long,
    val content: String,
)