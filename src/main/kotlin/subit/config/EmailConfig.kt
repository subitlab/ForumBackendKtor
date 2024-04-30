package subit.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.mamoe.yamlkt.Comment
import java.util.regex.Pattern

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
    val codeValidTime: Long,
    @Comment("验证邮件标题")
    val verifyEmailTitle: String,
    @Comment("用户邮箱格式要求(正则表达式)")
    val emailFormat: String,
    @Transient
    val pattern: Pattern = Pattern.compile(emailFormat)
)

var emailConfig: EmailConfig by config(
    "email.yml",
    EmailConfig(
        host = "smtp.office365.com",
        port = 587,
        sender = "example@email.server.com",
        password = "your_email_password",
        codeValidTime = 600,
        verifyEmailTitle = "论坛验证码",
        emailFormat = ".*@.*\\..*"
    )
)