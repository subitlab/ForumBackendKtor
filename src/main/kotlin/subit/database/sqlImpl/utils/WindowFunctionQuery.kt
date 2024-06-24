package subit.database.sqlImpl.utils

import org.jetbrains.exposed.sql.*
import subit.database.sqlImpl.utils.WindowFunctionQuery.Companion.rowNumber
import subit.database.sqlImpl.utils.WindowFunctionQuery.Companion.totalCount
import java.util.*

/**
 * 表示窗口函数查询, 将给定的查询转换为窗口函数查询, 并从指定位置开始, 限制大小获取数据
 *
 * 例如:
 * 对于以下查询
 * ```sql
 * SELECT a.*, b.*
 * FROM table_a a
 * JOIN table_b b ON a.id = b.a_id
 * WHERE a.some_column = 'some_value'
 * GROUP BY a.id, b.id
 * HAVING COUNT(b.id) > 1
 * ORDER BY a.created_at DESC
 * ```
 * 可以使用以下代码获取第0条开始的10条数据
 * ```kotlin
 * val query = WindowFunctionQuery(rawQuery, 0 10)
 * val result: List<ResultRow> = query.toList()
 * ```
 * 通过这种方式产生的sql
 * ```sql
 * SELECT *
 * FROM (
 *   SELECT a.*, b.*, COUNT(*) OVER () AS total_count, ROW_NUMBER() OVER (ORDER BY a.created_at DESC) AS row_num
 *   FROM table_a a
 *   JOIN table_b b ON a.id = b.a_id
 *   WHERE a.some_column = 'some_value'
 *   GROUP BY a.id, b.id
 *   HAVING COUNT(b.id) > 1
 * ) subquery
 * WHERE row_num BETWEEN 1 AND 10
 * ```
 * 查询返回的列相比rawQuery多了`total_count`和`row_num`, `total_count`表示总数据量, `row_num`表示当前数据的行号.
 * 这两项可通过[totalCount]和[rowNumber]获取
 *
 * 例如:
 * ```kotlin
 * val query = WindowFunctionQuery(rawQuery, 0, 10)
 * val result: List<ResultRow> = query.toList()
 * val totalCount = result.firstOrNull()?.getOrNull(WindowFunctionQuery.totalCount) ?: error("获取总数据量失败")
 * val withRowNumber: List<Pair<Long, ResultRow>> = result.map { it[WindowFunctionQuery.rowNumber] to it }
 * ```
 * @param rawQuery 原始查询
 * @param begin 起始位置
 * @param sizeLimit 限制大小
 */
class WindowFunctionQuery(
    rawQuery: Query,
    begin: Long,
    sizeLimit: Int
): Query(
    /**
     * 第一个参数是一个FieldSet, 该FieldSet的第一个参数是source, 即sql中的from子句中的内容, 第二个参数是要查询的字段即select子句中的内容
     *
     * 实际上`SELECT`子句中唯一字段是`*`, 但`*`并非一个列, 而是代表了子查询中选择的所有列, 所以Slice的fields需要传入子查询的所有列.
     * 否则查询的字段只有`*`一个, 而实际返回的字段有若干, 这将导致无法从查询获得的`resultRow`中获取数据.
     * 所以这里使用子查询的所有列, 但在真正查询时使用`*`代替. 可以参考[queryToExecute]属性.
     *
     * from中的内容为原始查询加上`total_count`和`row_number`两个字段, 并去掉`order by`子句
     * (因为`order by`相当于在`row_number`上排序, 可以参考上面的转换示例)
     */
    Slice(
        // 第一个参数是source, 即from子句中的内容, 即原始查询进行了一些修改
        rawQuery.copy().apply { // copy一份原始查询, 用于修改
            val orderBy = rawQuery.orderByExpressions // 获取原始查询的order by
            this.set = Slice(
                // 子查询的from内容即原始查询的from内容
                rawQuery.set.source,
                // 子查询的fields为原始查询的fields加上total_count和row_number
                rawQuery.set.fields + makeTotalCount + makeRowNumber(Collections.unmodifiableList(orderBy))
            )
            // 清空order by
            (this.orderByExpressions as MutableList).clear()
        }.alias("subquery"), // 将修改后的查询取别名为subquery
        // 第二个参数是要查询的字段, 即select子句中的内容, 这里使用子查询中的所有字段
        rawQuery.set.fields + totalCount + rowNumber
    ),
    /**
     * 第二个参数是where子句中的内容, 原始查询的where已经在子查询了, 这里的where是设置row_number的范围
     */
    rowNumber between (longParam(begin + 1) to longParam(begin + sizeLimit))
)
{
    companion object
    {
        /**
         * 任意表达式, 用于在select子句中表示`*`
         */
        private val any = object: Expression<Nothing>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("*") }
        }

        /**
         * 总数据量字段
         */
        val totalCount = object: Expression<Long>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("total_count") }
        }

        /**
         * 生成总数据量字段的表达式
         */
        private val makeTotalCount = object: Expression<Long>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) =
                queryBuilder { append("COUNT(*) OVER () AS total_count") }
        }

        /**
         * 行号字段
         */
        val rowNumber = object: Expression<Long>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("row_number") }
        }


        /**
         * 生成行号字段的表达式
         */
        private fun makeRowNumber(orderBy: List<Pair<Expression<*>, SortOrder>>) = object: Expression<Long>()
        {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) =
                queryBuilder {
                    append("ROW_NUMBER() OVER (")
                    if (orderBy.isNotEmpty()) append("ORDER BY")
                    orderBy.forEachIndexed { index, (expression, order) ->
                        if (index != 0) append(", ")
                        append("(")
                        append(expression)
                        append(") ")
                        append(order.code)
                    }
                    append(") AS row_number")
                }
        }

        /**
         * 生成`between`表达式
         */
        private infix fun Expression<*>.between(range: Pair<Expression<*>, Expression<*>>): Op<Boolean> =
            object: Op<Boolean>()
            {
                override fun toQueryBuilder(queryBuilder: QueryBuilder) =
                    queryBuilder {
                        append("(")
                        append(this@between)
                        append(") BETWEEN (")
                        append(range.first)
                        append(") AND (")
                        append(range.second)
                        append(")")
                    }
            }
    }

    /**
     * 转换为真正的查询, 即将选择的字段用`*`代替
     */
    public override val queryToExecute: Query
        get() = copy().adjustSelect { select(any) }

    override fun prepareSQL(builder: QueryBuilder): String = queryToExecute.prepareSQL(builder)
}