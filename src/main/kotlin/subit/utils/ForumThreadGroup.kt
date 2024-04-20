package subit.utils

import kotlinx.coroutines.runBlocking
import subit.logger.ForumLogger

object ForumThreadGroup: ThreadGroup("ForumThreadGroup")
{
    override fun uncaughtException(t: Thread, e: Throwable)
    {
        ForumLogger.err.println("Thread ${t.name} threw an uncaught exception: ${e.message}")
        e.printStackTrace(ForumLogger.err)
    }

    fun newThread(name: String, block: ()->Unit) = Thread(this, block, name)
    fun newThread(block: ()->Unit) = Thread(this, block)

    /**
     * 任务
     * @property name 任务名(唯一)
     * @property delay 延迟时间(毫秒)
     * @property interval 间隔时间(毫秒)
     * @property times 重复次数(null为无限)
     * @property block 任务
     */
    data class Task(
        val name: String,
        val delay: Long = 0,
        val interval: Long,
        val times: UInt? = null,
        val block: suspend ()->Unit
    )
    {
        fun start() = startTask(this)
        override fun equals(other: Any?): Boolean = other is Task && other.name == name
        override fun hashCode(): Int = name.hashCode()
    }

    val tasks = mutableMapOf<Task,Thread>()

    /**
     * 启动一个任务
     * @param task 任务
     * @return 是否成功启动
     */
    fun startTask(task: Task): Boolean
    {
        if (tasks.containsKey(task)) return false
        val thread = newThread(task.name) {
            Thread.sleep(task.delay)
            var times = task.times
            while (true)
            {
                runBlocking { task.block () }
                times?.let { times-- }
                if (times == 0u) break
                runCatching { Thread.sleep(task.interval) }.onFailure()
                {
                    if (it !is InterruptedException) throw it
                    return@newThread
                }
            }
        }.apply { start() }
        tasks[task] = thread
        return true
    }

    fun startOrReplaceTask(task: Task)
    {
        stopTask(task)
        startTask(task)
    }

    fun stopTask(task: Task)
    {
        tasks[task]?.interrupt()
        tasks.remove(task)
    }
}