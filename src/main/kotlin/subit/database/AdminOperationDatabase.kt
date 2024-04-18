package subit.database

import subit.dataClasses.UserId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object AdminOperationDatabase: DataAccessObject<AdminOperationDatabase.AdminOperations>(AdminOperations)
{
    object AdminOperations: Table("admin_operations")
    {
        val admin = reference("admin", UserDatabase.Users).index()
        val operationType = varchar("operation_type", 255)
        val operation = text("operation")
        val time = timestamp("time").defaultExpression(CurrentTimestamp()).index()
    }

    val operationSerializer = Json()
    {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend inline fun <reified T> addOperation(admin: UserId, operation: T) = query()
    {
        insert {
            it[AdminOperations.admin] = admin
            it[AdminOperations.operationType] = T::class.java.name
            it[AdminOperations.operation] = if (T::class.objectInstance == null) "" else operationSerializer.encodeToString(operation)
        }
    }
}