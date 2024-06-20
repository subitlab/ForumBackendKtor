package subit.database.memoryImpl

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import subit.config.emailConfig
import subit.database.EmailCodes
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup
import java.util.*

class EmailCodesImpl: EmailCodes
{
    private val codes = Collections.synchronizedMap(
        hashMapOf<Pair<String, EmailCodes.EmailCodeUsage>, Pair<String, Instant>>()
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
        codes.entries.removeIf { it.value.second < Clock.System.now() }
    }

    override suspend fun addEmailCode(email: String, code: String, usage: EmailCodes.EmailCodeUsage)
    {
        codes[email to usage] = code to Clock.System.now().plus(emailConfig.codeValidTime, DateTimeUnit.SECOND)
    }

    override suspend fun verifyEmailCode(email: String, code: String, usage: EmailCodes.EmailCodeUsage): Boolean
    {
        val pair = codes[email to usage] ?: return false
        if (pair.first != code) return false
        if (pair.second < Clock.System.now()) return false
        codes.remove(email to usage)
        return true
    }
}