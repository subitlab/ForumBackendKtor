package subit.dataClasses

import kotlinx.serialization.Serializable

/**
 * 切片, 若查询的数据量过大, 则以切片返回.
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
    companion object
    {
        /**
         * 生成一个空切片
         */
        fun <T> empty() = Slice<T>(0, 0, emptyList())
        inline fun <T> Sequence<T>.asSlice(begin: Long, limit: Int, filter: (T)->Boolean = { true }): Slice<T> =
            asIterable().asSlice(begin, limit, filter)

        inline fun <T> Iterable<T>.asSlice(begin: Long, limit: Int, filter: (T)->Boolean = { true }): Slice<T> =
            fromIterable(this, begin, limit, filter)

        /**
         * 敬告接手的程序员: 由于此类针对数据量很大, 不适合全部加载到内存中的情况, 所以实现时请注意不要将数据加载到内存中
         * 例如 [Iterable.drop] [Iterable.map] 等方法会创建一个list将整个数据加载到内存中, 可能导致内存溢出或占用过大, 此方法的实现避免了这个问题
         * 但这个方法仍需要遍历整个数据, 因此请确保数据量不会过大
         *
         * 传入[filter]而不使用[Iterable.filter]的意义在于, 后者会将所有满足条件的数据加载到内存中,
         * 而这里的[filter]则是在遍历时进行过滤, 对于满足条件但不在 [begin] [limit] 范围内的数据不会加载到内存中
         */
        inline fun <T> fromIterable(iterable: Iterable<T>, begin: Long, limit: Int, filter: (T)->Boolean = { true }): Slice<T>
        {
            val list = ArrayList<T>(limit)
            var i = 0L
            for (item in iterable)
            {
                if (!filter(item)) continue
                if (i >= begin && i < begin+limit) list.add(item)
                i++
            }
            return Slice(i, begin, list)
        }
    }

    fun <R> map(transform: (T)->R) = Slice(totalSize, begin, list.map(transform))
}

fun <T> Iterable<Iterable<T>>.flattenAsIterable(): Iterable<T> = iterator {
    for (i in this@flattenAsIterable) for (j in i) yield(j)
}.asSequence().asIterable()