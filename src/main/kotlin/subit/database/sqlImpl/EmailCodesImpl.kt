package subit.database.sqlImpl

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.KoinComponent
import subit.config.emailConfig
import subit.dataClasses.Slice.Companion.singleOrNull
import subit.database.EmailCodes
import subit.database.EmailCodes.EmailCodeUsage
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup

class EmailCodesImpl: DaoSqlImpl<EmailCodesImpl.EmailsTable>(EmailsTable), EmailCodes, KoinComponent
{
    object EmailsTable: Table("email_codes")
    {
        val email = varchar("email", 100).index()
        val code = varchar("code", 10)
        val time = timestamp("time").index()
        val usage = enumeration("usage", EmailCodeUsage::class)
    }

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
    }

    override suspend fun addEmailCode(email: String, code: String, usage: EmailCodeUsage): Unit = query()
    {
        insert {
            it[EmailsTable.email] = email
            it[EmailsTable.code] = code
            it[EmailsTable.usage] = usage
            it[time] = Clock.System.now().plus(emailConfig.codeValidTime, unit = DateTimeUnit.SECOND)
        }
    }

    /**
     * 验证邮箱验证码，验证成功后将立即删除验证码
     */
    override suspend fun verifyEmailCode(email: String, code: String, usage: EmailCodeUsage): Boolean = query()
    {
        val result = select(time).where {
            (EmailsTable.email eq email) and (EmailsTable.code eq code) and (EmailsTable.usage eq usage)
        }.singleOrNull()?.let { it[time] }

        if (result != null)
        {
            EmailsTable.deleteWhere {
                (EmailsTable.email eq email) and (EmailsTable.code eq code) and (EmailsTable.usage eq usage)
            }
        }

        result != null && result >= Clock.System.now()
    }

    private suspend fun clearExpiredEmailCode(): Unit = query()
    {
        EmailsTable.deleteWhere { time lessEq CurrentTimestamp }
    }
}