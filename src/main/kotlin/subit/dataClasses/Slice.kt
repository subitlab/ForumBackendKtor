package subit.dataClasses

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import subit.dataClasses.Slice.Companion.asSlice
import subit.dataClasses.Slice.Companion.fromSequence
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * 切片, 若查询的数据量过大, 则以切片返回.
 *
 * 此类针对数据量很大, 不适合全部加载到内存中的情况, 例如帖子列表, 用户列表等
 * @property totalSize 总数据量. 例如总帖子数, 总用户数, 注意是总数据量, 不是当前切片的数据量
 * @property begin 当前切片的起始位置
 * @property list 当前切片的数据
 */
@Serializable
data class Slice<T>(
    val totalSize: Long,
    val begin: Long,
    val list: List<T>
)
{
    @OptIn(ExperimentalContracts::class)
    @Suppress("unused")
    companion object
    {
        /**
         * 生成一个空切片
         */
        fun <T> empty() = Slice<T>(0, 0, emptyList())
        inline fun <T> Sequence<T>.asSlice(begin: Long, limit: Int, filter: (T)->Boolean = { true }): Slice<T>
        {
            contract {
                callsInPlace(filter, kotlin.contracts.InvocationKind.UNKNOWN)
            }
            return fromSequence(this, begin, limit, filter)
        }

        /**
         * 对于[Query], 且无需过滤的情况, 可以使用此方法, 可以避免[fromSequence]方法遍历所有数据的情况
         */
        fun Query.asSlice(begin: Long, limit: Int): Slice<ResultRow>
        {
            val sum: Long = copy().count()
            val list = this.limit(limit, begin).toList()
            return Slice(sum, begin, list)
        }

        @Deprecated(
            message = "该方法效率极低, 为实现过虑将通过分块查询遍历全部数据, " +
                      "虽不会造成很大的内存消耗, 但会阻塞进行查询造成卡顿",
            level = DeprecationLevel.WARNING
        )
        inline fun Query.asSlice(begin: Long, limit: Int, filter: (ResultRow)->Boolean): Slice<ResultRow>
        {
            contract {
                callsInPlace(filter, kotlin.contracts.InvocationKind.UNKNOWN)
            }
            // 尝试进行分块查询, 若不支持就只能直接查询了
            val res: Sequence<ResultRow> = runCatching()
            {
                this.fetchBatchedResults().flattenAsIterable()
            }.getOrDefault(this).asSequence()
            return fromSequence(res, begin, limit, filter)
        }

        /**
         * 敬告接手的程序员: 由于此类针对数据量很大, 不适合全部加载到内存中的情况, 所以实现时请注意不要将数据加载到内存中
         * 例如 [Iterable.drop] [Iterable.map] 等方法会创建一个list将整个数据加载到内存中, 可能导致内存溢出或占用过大, 此方法的实现避免了这个问题
         * 但这个方法仍需要遍历整个数据, 因此请确保数据量不会过大
         *
         * 传入[filter]而不使用[Iterable.filter]的意义在于, 后者会将所有满足条件的数据加载到内存中,
         * 而这里的[filter]则是在遍历时进行过滤, 对于满足条件但不在 [begin] / [limit] 范围内的数据不会加载到内存中
         */
        inline fun <T> fromSequence(
            sequence: Sequence<T>,
            begin: Long,
            limit: Int,
            filter: (T)->Boolean = { true }
        ): Slice<T>
        {
            contract {
                callsInPlace(filter, kotlin.contracts.InvocationKind.UNKNOWN)
            }
            val list = ArrayList<T>()
            var i = 0L
            for (item in sequence)
            {
                if (!filter(item)) continue
                if (i >= begin && i < begin+limit) list.add(item)
                i++
            }
            return Slice(i, begin, list)
        }

        fun Query.single() = asSlice(0, 1).list[0]
        fun Query.singleOrNull() = asSlice(0, 1).run { if (list.isEmpty()) null else list[0] }
    }

    fun <R> map(transform: (T)->R) = Slice(totalSize, begin, list.map(transform))
}

fun <T> sliceOf(vararg items: T) = items.toList().asSequence().asSlice(begin = 0, limit = items.size)

/**
 * [Table.selectBatched]等方法会返回一个 Iterable<Iterable<T>> 类型的数据, 此方法将其扁平化为 Iterable<T>
 *
 * 注意: 不使用[Iterable.flatten]的原因是, [Iterable.flatten]转为list
 */
fun <T> Iterable<Iterable<T>>.flattenAsIterable(): Iterable<T> = iterator {
    for (i in this@flattenAsIterable) for (j in i) yield(j)
}.asSequence().asIterable()