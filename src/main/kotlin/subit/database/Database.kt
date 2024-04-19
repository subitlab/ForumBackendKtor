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
        transaction(DatabaseSingleton.database) { SchemaUtils.create(table) }
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
    lateinit var database: Database
        private set

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
        validate()
    })

    fun initDatabase(config: ApplicationConfig)
    {
        database = Database.connect(
            createHikariDataSource(
                config.property("datasource.url").getString(),
                config.property("datasource.driver").getString(),
                config.property("datasource.user").getString(),
                config.property("datasource.password").getString()
            )
        )

        // 在此处写一下所有数据库, 这样就可以自动执行每个数据库的init, 从而初始化所有表
        BlockDatabase
        EmailCodeDatabase
        PostDatabase
        StarDatabase
        UserDatabase
        WhitelistDatabase
    }
}