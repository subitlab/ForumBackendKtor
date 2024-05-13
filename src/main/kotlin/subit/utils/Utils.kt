package subit.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import subit.config.emailConfig
import subit.database.EmailCodes
import java.util.*
import javax.mail.Address
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * 检查邮箱格式是否正确
 */
fun checkEmail(email: String): Boolean = emailConfig.pattern.matcher(email).matches()

/**
 * 检查密码是否合法
 * 要求密码长度在 6-20 之间，且仅包含数字、字母和特殊字符 !@#$%^&*()_+-=
 */
fun checkPassword(password: String): Boolean =
    password.length in 8..20 &&
    password.all { it.isLetterOrDigit() || it in "!@#$%^&*()_+-=" }

/**
 * 检查用户名是否合法
 * 要求用户名长度在 2-15 之间，且仅包含中文、数字、字母和特殊字符 _-.
 */
fun checkUsername(username: String): Boolean =
    username.length in 2..20 &&
    username.all { it in '\u4e00'..'\u9fa5' || it.isLetterOrDigit() || it in "_-." }

fun checkUserInfo(username: String, password: String, email: String): HttpStatus
{
    if (!checkEmail(email)) return HttpStatus.EmailFormatError
    if (!checkPassword(password)) return HttpStatus.PasswordFormatError
    if (!checkUsername(username)) return HttpStatus.UsernameFormatError
    return HttpStatus.OK
}

fun String?.toUUIDOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

suspend fun sendEmail(email: String, code: String, usage: EmailCodes.EmailCodeUsage) = withContext(Dispatchers.IO)
{
    val props = Properties()
    props.setProperty("mail.debug", "true")
    props.setProperty("mail.smtp.auth", "true")
    props.setProperty("mail.host", emailConfig.host)
    props.setProperty("mail.port", emailConfig.port.toString())
    props.setProperty("mail.smtp.starttls.enable", "true")
    val session = Session.getInstance(props)
    val message = MimeMessage(session)
    message.setFrom(InternetAddress(emailConfig.sender))
    message.setRecipient(Message.RecipientType.TO, InternetAddress(email))
    message.subject = emailConfig.verifyEmailTitle
    val multipart = MimeMultipart()
    val bodyPart = MimeBodyPart()
    bodyPart.setText(
        """
            您的验证码为: $code
            有效期为: ${emailConfig.codeValidTime}秒
            此验证码仅用于论坛${usage.description}，请勿泄露给他人。若非本人操作，请忽略此邮件。
        """.trimIndent()
    )
    multipart.addBodyPart(bodyPart)
    message.setContent(multipart)
    val transport = session.getTransport("smtp")
    transport.connect(emailConfig.host, emailConfig.sender, emailConfig.password)
    transport.sendMessage(message, arrayOf<Address>(InternetAddress(email)))
    transport.close()
}