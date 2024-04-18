package subit.database

import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Comment
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.javatime.timestamp
import subit.Loader
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup
import subit.utils.emailPattern
import java.time.Instant
import java.util.*
import java.util.regex.Pattern
import javax.mail.Address
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object EmailCodeDatabase: DataAccessObject<EmailCodeDatabase.EmailCodes>(EmailCodes)
{
    object EmailCodes: Table("email_codes")
    {
        val email = varchar("email", 100).index()
        val code = varchar("code", 10)
        val time = timestamp("time").index()
        val usage = enumeration("usage", EmailCodeUsage::class)
    }

    enum class EmailCodeUsage(val description: String)
    {
        LOGIN("登录"),
        REGISTER("注册"),
        RESET_PASSWORD("重置密码"),
    }

    @Serializable
    data class EmailConfig(
        @Comment("SMTP服务器地址")
        val host: String,
        @Comment("SMTP服务器端口")
        val port: Int,
        @Comment("发件人邮箱")
        val sender: String,
        @Comment("发件人邮箱密码")
        val password: String,
        @Comment("验证码有效期(秒)")
        val codeValidTime: Long = 600,
        @Comment("验证邮件标题")
        val verifyEmailTitle: String = "论坛验证码",
        @Comment("用户邮箱格式要求(正则表达式)")
        val emailFormat: String = ".*@.*\\..*",
    )
    {
        companion object
        {
            val example: EmailConfig = EmailConfig(
                host = "smtp.office365.com",
                port = 587,
                sender = "example@email.server.com",
                password = "your_email_password"
            )
        }
    }

    var config: EmailConfig
        private set

    init
    {
        // 启动定期清理过期验证码任务
        ForumThreadGroup.startTask(
            ForumThreadGroup.Task(
                name = "ClearExpiredEmailCode",
                interval = 1000/*ms*/*60/*s*/*5,/*m*/
            )
            {
                ForumLogger.config("Clearing expired email codes")
                ForumLogger.severe("Failed to clear expired email codes") { clearExpiredEmailCode() }
            }
        )
        config = Loader.getConfigOrCreate("email.yml", EmailConfig.example)
        Loader.reloadTasks.add(::reloadConfig)
    }

    private fun reloadConfig()
    {
        config = Loader.getConfigOrCreate("email.yml", EmailConfig.example)
        emailPattern = Pattern.compile(config.emailFormat)
        ForumLogger.config("Email config reloaded")
    }

    suspend fun sendEmailCode(email: String, usage: EmailCodeUsage): Unit = query()
    {
        val code = (1..6).map { ('0'..'9').random() }.joinToString("")

        sendEmailCode(email, code, usage)

        EmailCodes.insert {
            it[EmailCodes.email] = email
            it[EmailCodes.code] = code
            it[EmailCodes.usage] = usage
            it[time] = Instant.now().plusSeconds(config.codeValidTime)
        }
    }

    private fun sendEmailCode(email: String, code: String, usage: EmailCodeUsage)
    {
        // 发送邮件
        val props = Properties()
        props.setProperty("mail.debug", "true")
        props.setProperty("mail.smtp.auth", "true")
        props.setProperty("mail.host", config.host)
        props.setProperty("mail.port", config.port.toString())
        props.setProperty("mail.smtp.starttls.enable", "true")
        val session = Session.getInstance(props)
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.sender))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(email))
        message.subject = config.verifyEmailTitle
        val multipart = MimeMultipart()
        val bodyPart = MimeBodyPart()
        bodyPart.setText(
            """
            您的验证码为: $code
            有效期为: ${config.codeValidTime}秒
            此验证码仅用于论坛${usage.description}，请勿泄露给他人。若非本人操作，请忽略此邮件。
        """.trimIndent()
        )
        multipart.addBodyPart(bodyPart)
        message.setContent(multipart)
        val transport = session.getTransport("smtp")
        transport.connect(config.host, config.sender, config.password)
        transport.sendMessage(message, arrayOf<Address>(InternetAddress(email)))
        transport.close()
    }

    /**
     * 验证邮箱验证码，验证成功后将立即删除验证码
     */
    suspend fun verifyEmailCode(email: String, code: String, usage: EmailCodeUsage): Boolean = query()
    {
        val result = EmailCodes.select {
            (EmailCodes.email eq email) and (EmailCodes.code eq code) and (EmailCodes.usage eq usage)
        }.singleOrNull()?.let { it[time] }

        if (result != null)
        {
            EmailCodes.deleteWhere {
                (EmailCodes.email eq email) and (EmailCodes.code eq code) and (EmailCodes.usage eq usage)
            }
        }

        result != null && result >= Instant.now()
    }

    private suspend fun clearExpiredEmailCode(): Unit = query()
    {
        EmailCodes.deleteWhere { time lessEq Instant.now() }
    }
}