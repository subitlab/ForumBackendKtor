package subit.database.memoryImpl

import subit.dataClasses.UserId
import subit.database.Operations
import java.util.Collections
import kotlin.reflect.KType

class OperationsImpl: Operations
{
    data class Operation<T>(val admin: UserId, val operation: T, val type: KType)
    private val operations = Collections.synchronizedList(mutableListOf<Operation<*>>())

    override suspend fun <T> addOperation(admin: UserId, operation: T, type: KType)
    {
        operations.add(Operation(admin, operation, type))
    }

    @Suppress("unused")
    fun getOperations(admin: UserId? = null): List<*>
    {
        return operations.filter { admin == null || it.admin == admin }
    }
}