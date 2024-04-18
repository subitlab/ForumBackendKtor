package subit

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import subit.database.UserDatabase
import subit.database.WhitelistDatabase
import kotlin.test.Test

class ForumBackendTest
{
    companion object
    {
        private const val TEST_EMAIL = "test@pkuschool.edu.cn"
        private const val TEST_PASSWORD = "test1234"
        private const val TEST_USERNAME = "test"
    }

    @Serializable
    data class LoginResponse(val token: String)

    private suspend fun clearDatabase()
    {
        WhitelistDatabase.query { deleteAll() }
        UserDatabase.query { deleteAll() }
    }

    private suspend fun setWhiteList()
    {
        WhitelistDatabase.add(TEST_EMAIL)
    }

    @Test
    fun test() = Unit
    fun test0() = testApplication()
    {
        val client = createClient()
        {
            install(ContentNegotiation)
            {
                json(Json()
                {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }

        val token = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJBdXRoZW50aWNhdGlvbiIsImlkIjoxLCJwYXNzd29yZCI6InRlc3QxMjM0IiwiZXhwIjoxNzEyOTQyODA3fQ.gE5ZoNs0qzb_rUqwJJY0KLBrZQbwEZEZToPwuqKYkQvdhIPcQDs2fgn3G5ygvsjkIsFCqATtucgtebt5GOJuLg"

        client.get("/user/info/0")
        {
            this.header("Content-Type", "application/json")
            this.header("Authorization", "Bearer $token")
        }.apply {
            println(status)
            println(bodyAsText())
        }
    }
}