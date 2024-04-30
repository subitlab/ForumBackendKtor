package subit.config

import kotlinx.serialization.Serializable

@Serializable
data class ApiDocsConfig(val name: String = "username", val password: String = "password")

var apiDocsConfig: ApiDocsConfig by config("api_docs.yml", ApiDocsConfig())