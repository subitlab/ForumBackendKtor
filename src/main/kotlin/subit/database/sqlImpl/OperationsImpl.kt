package subit.database.sqlImpl

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.KoinComponent
import subit.dataClasses.UserId
import subit.database.Operations
import kotlin.reflect.KType

class OperationsImpl: DaoSqlImpl<OperationsImpl.OperationsTable>(OperationsTable), Operations, KoinComponent
{
    object OperationsTable: Table("operations")
    {
        val admin = reference("operator", UsersImpl.UserTable).index()
        val operationType = varchar("operation_type", 255)
        val operation = text("operation")
        val time = timestamp("time").defaultExpression(CurrentTimestamp).index()
    }

    private val operationSerializer = Json()
    {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override suspend fun <T> addOperation(admin: UserId, operation: T, type: KType): Unit = query()
    {
        insert {
            it[OperationsTable.admin] = admin
            it[operationType] = type.toString()
            it[OperationsTable.operation] =
                if (type.classifier == null) "" else operationSerializer.encodeToString(serializer(type), operation)
        }
    }
}