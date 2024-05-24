package subit.database.memoryImpl

import subit.config.emailConfig
import subit.database.EmailCodes
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup
import java.util.*

class EmailCodesImpl: EmailCodes
{
    private val codes = Collections.synchronizedMap(
        hashMapOf<Pair<String, EmailCodes.EmailCodeUsage>, Pair<String, Date>>()
    )

    init
    {
        val logger = ForumLogger.getLogger()
        // 启动定期清理过期验证码任务
        ForumThreadGroup.startTask(
            ForumThreadGroup.Task(
                name = "ClearExpiredEmailCode",
                interval = 1000/*ms*/*60/*s*/*5,/*m*/
            )
            {
                logger.config("Clearing expired email codes")
                logger.severe("Failed to clear expired email codes") { clearExpiredEmailCode() }
            }
        )
    }

    private fun clearExpiredEmailCode()
    {
        val now = Date()
        codes.entries.removeIf { it.value.second.before(now) }
    }

    override suspend fun addEmailCode(email: String, code: String, usage: EmailCodes.EmailCodeUsage)
    {
        codes[email to usage] = code to Date(System.currentTimeMillis()+emailConfig.codeValidTime*1000)
    }

    override suspend fun verifyEmailCode(email: String, code: String, usage: EmailCodes.EmailCodeUsage): Boolean
    {
        val pair = codes[email to usage] ?: return false
        if (pair.first != code) return false
        if (pair.second.before(Date())) return false
        codes.remove(email to usage)
        return true
    }
}