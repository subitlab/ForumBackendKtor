package subit.utils

import io.ktor.http.*
import java.util.regex.Pattern

private val emailPattern = Pattern.compile(".+@(i\\.)?pkuschool\\.edu\\.cn")

/**
 * 检查邮箱格式是否正确
 * 要求邮箱为 i.pkuschool.edu.cn 或 pkuschool.edu.cn 结尾
 */
fun checkEmail(email: String): Boolean = emailPattern.matcher(email).matches()

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

fun checkUserInfo(username: String, password: String, email: String): HttpStatusCode
{
    if (!checkEmail(email)) return HttpStatus.EmailFormatError
    if (!checkPassword(password)) return HttpStatus.PasswordFormatError
    if (!checkUsername(username)) return HttpStatus.UsernameFormatError
    return HttpStatusCode.OK
}