package subit.database

import subit.dataClasses.UserId
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * 操作记录
 *
 * 对于发帖等操作, 因为帖子将永远保存在数据库中, 所以不需要记录操作记录.
 * 需要保存的是删除板块, 删除用户等操作, 这些操作将会对数据库中的数据产生永久性影响, 所以需要记录.
 * 因为只是留作备份, 所以没有查询功能, 需要查询时请直接查询数据库.
 * 现在存储是将对象直接存入, 实现应将类型和对象序列化后存入数据库.
 * 且保证数据易反序列, 或序列化为json等易读格式.
 */
interface Operations
{
    suspend fun <T> addOperation(admin: UserId, operation: T, type: KType)
}

suspend inline fun <reified T> Operations.addOperation(admin: UserId, operation: T) =
    addOperation(admin, operation, typeOf<T>())

