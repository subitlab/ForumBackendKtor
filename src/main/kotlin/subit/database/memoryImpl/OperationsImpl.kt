package subit.database.memoryImpl

import kotlinx.serialization.Serializable
import subit.dataClasses.UserId
import subit.database.Operations
import java.util.*
import kotlin.reflect.KType

class OperationsImpl: Operations
{
    @Serializable
    data class Operation<T>(val admin: UserId, val operation: T, val type: KType)
    private val operations = Collections.synchronizedList(mutableListOf<Operation<*>>())

    override suspend fun <T> addOperation(admin: UserId, operation: T, type: KType)
    {
        operations.add(Operation(admin, operation, type))
    }

    @Suppress("unused")
    fun getOperations(admin: UserId? = null): List<*> = operations.filter { admin == null || it.admin == admin }
}