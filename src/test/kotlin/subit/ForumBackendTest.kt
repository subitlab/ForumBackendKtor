package subit

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.deleteAll
import subit.database.PermissionLevel
import subit.database.UserDatabase
import subit.database.WhitelistDatabase
import subit.utils.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation)
            {
                json(Json()
                {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        client.get("/")

        clearDatabase()
        setWhiteList()

        val token = client.post("/auth/register")
        {
            this.header("Content-Type", "application/json")
            this.setBody(
                buildJsonObject {
                    put("password", TEST_PASSWORD)
                    put("email", TEST_EMAIL)
                    put("username", TEST_USERNAME)
                    put("code", "123456")
                }.toString()
            )
        }.let {
            println(it.status.description)
            it.body<LoginResponse>().token
        }

        val userID = UserDatabase.getUser(TEST_EMAIL)?.id ?: throw AssertionError("User not found")

        UserDatabase.changeUserPermission(
            id = userID,
            read = PermissionLevel.ROOT,
            post = PermissionLevel.ROOT,
            comment = PermissionLevel.ROOT,
            ask = PermissionLevel.ROOT,
            file = PermissionLevel.ROOT,
            delete = PermissionLevel.ROOT,
            anonymous = PermissionLevel.ROOT,
        )

        println(UserDatabase.getUser(userID)?.toPermission())

        client.post("/admin/user/changeUserPermission")
        {
            this.header("Content-Type", "application/json")
            this.header("Authorization", "Bearer $token")
            this.setBody(
                buildJsonObject {
                    put("id", userID)
                    put("read", PermissionLevel.SUPER_ADMIN.toString())
                    put("post", PermissionLevel.SUPER_ADMIN.toString())
                    put("comment", PermissionLevel.SUPER_ADMIN.toString())
                    put("ask", PermissionLevel.SUPER_ADMIN.toString())
                    put("file", PermissionLevel.SUPER_ADMIN.toString())
                    put("delete", PermissionLevel.SUPER_ADMIN.toString())
                    put("anonymous", PermissionLevel.SUPER_ADMIN.toString())
                }.toString()
            )
        }.apply {
            assertEquals(HttpStatus.OK, status)
        }

        client.get("/admin/user/getUserPermission")
        {
            this.header("Content-Type", "application/json")
            this.header("Authorization", "Bearer $token")
            this.parameter("id", userID.toString())
        }.apply {
            assertEquals(HttpStatus.OK, status)
            println(bodyAsText())
        }
    }
}