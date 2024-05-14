package subit.database

import kotlinx.serialization.Serializable
import subit.database.EmailCodes.EmailCodeUsage
import subit.utils.sendEmail

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
    sendEmail(email, code, usage)
    addEmailCode(email, code, usage)
}