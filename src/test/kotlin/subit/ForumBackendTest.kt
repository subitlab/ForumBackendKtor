package subit

import kotlinx.serialization.Serializable
import kotlin.test.Test

class ForumBackendTest
{
    @Serializable data class LoginResponse(val token: String)

    @Test
    fun test(){}
}
