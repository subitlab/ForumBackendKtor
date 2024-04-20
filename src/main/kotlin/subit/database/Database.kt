package subit.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import subit.logger.ForumLogger
import java.time.Instant

/**
 * @param T 表类型
 * @property table 表
 */
abstract class DataAccessObject<T: Table>(val table: T)
{
    suspend inline fun <R> query(crossinline block: suspend T.()->R) =
        newSuspendedTransaction(Dispatchers.IO) { block(table) }

    init // 创建表
    {
        transaction(DatabaseSingleton.database) { SchemaUtils.createMissingTablesAndColumns(table) }
    }
}

object InstantSerializer: KSerializer<Instant>
{
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) =
        encoder.encodeLong(value.toEpochMilli())
}

/**
 * 数据库单例
 */
object DatabaseSingleton
{
    /**
     * 数据库
     */
    val database: Database by lazy {
        Database.connect(
            createHikariDataSource(
                config.property("datasource.url").getString(),
                config.property("datasource.driver").getString(),
                config.property("datasource.user").getString(),
                config.property("datasource.password").getString()
            )
        )
    }
    private lateinit var config: ApplicationConfig

    /**
     * 创建Hikari数据源,即数据库连接池
     */
    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String,
        password: String
    ) = HikariDataSource(HikariConfig().apply {
        this.driverClassName = driver
        this.jdbcUrl = url
        this.username = user
        this.password = password
        this.maximumPoolSize = 3
        this.isAutoCommit = false
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        this.poolName = "subit"
        validate()
    })

    /**
     * 初始化数据库
     * @param config 配置
     * @param lazyInit 是否使用懒惰初始化, 数据库首次连接可能消耗大量时间, 采用懒惰初始化可以减少启动时间,
     * 但会导致部分配置文件不会在启动时自动生成且生成数据库结构时可能会出现延迟
     */
    fun initDatabase(config: ApplicationConfig, lazyInit: Boolean = true)
    {
        ForumLogger.config("Init database. LazyInit: $lazyInit")
        this.config = config
        if (!lazyInit) activate()
    }

    private fun activate()
    {
        AdminOperationDatabase.table
        BlockDatabase.table
        CommentDatabase.table
        EmailCodeDatabase.table
        LikesDatabase.table
        PermissionDatabase.table
        PostDatabase.table
        PrivateChatDatabase.table
        ProhibitDatabase.table
        ReportDatabase.table
        StarDatabase.table
        UserDatabase.table
        WhitelistDatabase.table
    }
}