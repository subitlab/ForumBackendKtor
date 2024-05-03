package subit.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.mamoe.yamlkt.Comment
import subit.logger.ForumLogger
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.regex.Pattern

@Serializable
data class LoggerConfig(
    @Comment("过滤器，根据whiteList决定符合哪些条件的log会被打印/过滤")
    val matchers: List<String> = arrayListOf(),
    @Comment("是否为白名单模式，如果为true，则只有符合matchers的log会被打印，否则只有不符合matchers的log会被打印")
    val whiteList: Boolean = true,
    @Comment("日志等级 (OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL)")
    @SerialName("level")
    val levelName: String = Level.INFO.name,
)
{
    @Transient
    val level: Level = Level.parse(levelName)
    @Transient
    val pattern: Pattern = Pattern.compile(matchers.joinToString("|") { "($it)" })

    fun check(record: LogRecord): Boolean = (matchers.isEmpty() || pattern.matcher(record.message).find() == whiteList)
}

var loggerConfig: LoggerConfig by config("logger.yml", LoggerConfig(), { _, new -> ForumLogger.logger.setLevel(new.level) })