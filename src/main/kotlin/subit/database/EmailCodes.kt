package subit.database

import kotlinx.serialization.Serializable
import subit.database.EmailCodes.EmailCodeUsage
import subit.logger.ForumLogger
import subit.utils.sendEmail

private val logger = ForumLogger.getLogger()

interface EmailCodes
{
    @Serializable
    enum class EmailCodeUsage(@Transient val description: String)
    {
        LOGIN("登录"),
        REGISTER("注册"),
        RESET_PASSWORD("重置密码"),
    }

    suspend fun addEmailCode(email: String, code: String, usage: EmailCodeUsage)

    /**
     * 验证邮箱验证码，验证成功后将立即删除验证码
     */
    suspend fun verifyEmailCode(email: String, code: String, usage: EmailCodeUsage): Boolean
}

suspend fun EmailCodes.sendEmailCode(email: String, usage: EmailCodeUsage)
{
    val code = (1..6).map { ('0'..'9').random() }.joinToString("")
    sendEmail(email, code, usage).invokeOnCompletion {
        if (it != null) logger.severe("发送邮件失败: email: $email, usage: $usage", it)
        else logger.info("发送邮件成功: $email, $code, $usage")
    }
    addEmailCode(email, code, usage)
}