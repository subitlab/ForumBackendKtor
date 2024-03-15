package subit.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import subit.ForumBackend
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * 数据库控制类,提供了一些便捷的方法
 * 例(序列化/反序列化到数据类):
 *
 * ```kotlin
 * data class MatchInfo(val id: Int, val name: String)
 * data class UpdateInfo(val id: Int, val name: String, val age: Int)
 * data class QueryInfo(val age: Int)
 *
 * val matchInfo = MatchInfo(1, "test")
 * val updateInfo = UpdateInfo(1, "test", 18)
 *
 * UserDatabase.query { update( where = match(matchInfo), body = from(updateInfo)) }
 * val queryInfo: QueryInfo = UserDatabase.query { select(match(matchInfo)).single().let{ deserialize<QueryInfo>(it) } }
 * ```
 *
 * 例(序列化/反序列化到Map):
 *
 * ```kotlin
 * val matchInfo = mapOf("id" to 1, "name" to "test")
 * val updateInfo = mapOf("id" to 1, "name" to "test", "age" to 18)
 *
 * UserDatabase.query { update(where = match(matchInfo), body = from(updateInfo)) } // 也可以直接 match("id" to 1, "name" to "test")
 * val queryInfo: Map<String, Any?> = UserDatabase.query { select(match(matchInfo)).single().let{ deserializeToMap(it) } }
 * ```
 *
 * @param T 表类型
 * @property table 表
 */
abstract class DatabaseController<T: Table>(val table: T)
{
    suspend inline fun <R> query(crossinline block: suspend T.()->R) = newSuspendedTransaction(Dispatchers.IO) { block(table) }

    init // 创建表
    {
        transaction(ForumBackend.database)
        {
            SchemaUtils.create(table)
        }
    }
}

inline fun <reified R: Any> Table.deserialize(resultRow: ResultRow): R
{
    val clazz: KClass<R> = R::class // 获取类
    if (!clazz.isData) throw IllegalArgumentException("Class ${clazz.qualifiedName} is not a data class") // 检查是否是数据类
    val constructor =
        clazz.primaryConstructor ?: throw IllegalArgumentException("Class ${clazz.qualifiedName} has no primary constructor") // 获取主构造器
    val params = constructor.parameters // 获取构造器参数
    val argsMap = mutableMapOf<KParameter, Any?>() // 保存对应参数
    val columns: Map<String, Column<*>> = this.columns.associateBy { it.name } // 所有列
    for (param in params)
    {
        val paramName = param.name ?: continue // 参数名,如果没有则跳过
        val paramType = param.type // 参数类型
        val value = columns[paramName]?.let { resultRow[it] }?.run() // 获取值,如果是EntityID则获取其value
        {
            if (this is EntityID<*>) this.value
            else this
        }
        if (paramType.isMarkedNullable&&value==null) argsMap[param] = null // 如果参数可空且值为空则赋值为null
        else if (value==null) continue // 如果参数不可空且值为空则跳过
        else if (paramType.classifier!=value::class)
            throw IllegalArgumentException("Parameter ${param.name} of ${clazz.qualifiedName} is not ${value::class.qualifiedName}") // 如果参数类型不匹配则抛出错误
        else argsMap[param] = value // 否则赋值
    }
    return constructor.callBy(argsMap)
}

fun Table.deserializeToMap(resultRow: ResultRow): Map<String, Any?>
{
    val map = mutableMapOf<String, Any?>()
    for (column in this.columns)
    {
        val value = resultRow[column] // 获取值
        if (value is EntityID<*>) map[column.name] = value.value // 如果是EntityID则获取其value
        else map[column.name] = value
    }
    return map
}

@Suppress("UNCHECKED_CAST")
inline fun <reified R: Any, reified T: Table> T.from(obj: R): T.(UpdateBuilder<*>)->Unit
{
    val clazz: KClass<R> = R::class // 获取类
    if (Map::class.isSuperclassOf(clazz)) runCatching { return from(obj as Map<String, Any?>) }
    if (!clazz.isData) throw IllegalArgumentException("Class ${clazz.qualifiedName} is not a data class") // 检查是否是数据类
    val columns = this@from.columns.associateBy { it.name } // 所有列
    return {
        for (field in clazz.memberProperties) // 遍历所有字段
        {
            val fieldName = field.name // 字段名
            val fieldValue = field.get(obj) // 字段值
            val column = (columns[fieldName] ?: continue) as Column<Any?> // 获取对应列
            if (fieldValue==null&&column.columnType.nullable) it[column] = null // 如果字段值为空且列可空则赋值为null
            else if (fieldValue!=null) it[column] = fieldValue // 否则赋值
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T: Table> T.from(map: Map<String, Any?>): T.(UpdateBuilder<*>)->Unit
{
    val columns = this.columns.associateBy { it.name }
    return {
        for ((key, value) in map)
        {
            val column = (columns[key] ?: continue) as Column<Any?> // 获取对应列
            if (value==null&&column.columnType.nullable) it[column] = null // 如果字段值为空且列可空则赋值为null
            else if (value!=null) it[column] = value // 否则赋值
        }
    }
}

inline fun <reified T: Table, K, V> T.from(vararg pairs: Pair<K, V>) = from(mapOf(*pairs))

@Suppress("UNCHECKED_CAST")
inline fun <reified R: Any, reified T: Table, I> T.match(obj: R): I.()->Op<Boolean>
{
    val clazz: KClass<R> = R::class // 获取类
    if (Map::class.isSuperclassOf(clazz)) runCatching { return this.match(obj as Map<String, Any?>) }
    if (!clazz.isData) throw IllegalArgumentException("Class ${clazz.qualifiedName} is not a data class") // 检查是否是数据类
    val fields = clazz.memberProperties // 获取所有字段
    val columns = this.columns.associateBy { it.name } // 所有列
    var op: Op<Boolean> = Op.TRUE // 操作
    for (field in fields) // 遍历所有字段
    {
        val fieldName = field.name // 字段名
        val fieldValue = field.get(obj) // 字段值
        val column = (columns[fieldName] ?: continue) as Column<Any?> // 获取对应列
        if (fieldValue==null&&column.columnType.nullable) op = op.and { column.isNull() } // 如果字段值为空且列可空则赋值为null
        else if (fieldValue!=null) op = op.and { column.eq(fieldValue) } // 否则赋值
    }
    return { op }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T: Table, I> T.match(map: Map<String, Any?>): I.()->Op<Boolean>
{
    val columns = this.columns.associateBy { it.name } // 所有列
    var op: Op<Boolean> = Op.TRUE // 操作
    for ((key, value) in map)
    {
        val column = (columns[key] ?: continue) as Column<Any?> // 获取对应列
        if (value==null&&column.columnType.nullable) op = op.and { column.isNull() } // 如果字段值为空且列可空则赋值为null
        else if (value!=null) op = op.and { column.eq(value) } // 否则赋值
    }
    return { op }
}

inline fun <reified T: Table, K, V, I> T.match(vararg pairs: Pair<K, V>): I.()->Op<Boolean> = match(mapOf(*pairs))