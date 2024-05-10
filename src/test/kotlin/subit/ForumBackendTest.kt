package subit

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json

class ForumBackendTest
{
    fun test() = testApplication()
    {
        createClient()
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
    }
}