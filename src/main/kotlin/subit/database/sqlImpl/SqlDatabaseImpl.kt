package subit.database.sqlImpl

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.ext.get
import subit.console.AnsiStyle.Companion.RESET
import subit.console.SimpleAnsiColor.Companion.CYAN
import subit.console.SimpleAnsiColor.Companion.GREEN
import subit.console.SimpleAnsiColor.Companion.RED
import subit.database.*
import subit.logger.ForumLogger
import subit.utils.ForumThreadGroup.shutdown

/**
 * @param T 表类型
 * @property table 表
 */
abstract class DaoSqlImpl<T: Table>(table: T): KoinComponent
{
    suspend inline fun <R> query(crossinline block: suspend T.()->R) = table.run {
        newSuspendedTransaction(Dispatchers.IO) { block() }
    }

    private val database: Database by inject()
    val table: T by lazy {
        transaction(database)
        {
            SchemaUtils.createMissingTablesAndColumns(table)
        }
        table
    }
}

/**
 * 数据库单例
 */
object SqlDatabaseImpl: IDatabase, KoinComponent
{
    /**
     * 数据库
     */
    private lateinit var config: ApplicationConfig

    /**
     * 创建Hikari数据源,即数据库连接池
     */
    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String?,
        password: String?
    ) = HikariDataSource(HikariConfig().apply {
        this.driverClassName = driver
        this.jdbcUrl = url
        if (user != null) this.username = user
        if (password != null) this.password = password
        this.maximumPoolSize = 3
        this.isAutoCommit = false
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        this.poolName = "subit"
        validate()
    })

    override val name: String = "sql"

    /**
     * 初始化数据库.
     */
    override fun Application.init()
    {
        config = environment.config
        val lazyInit = config.propertyOrNull("database.sql.lazyInit")?.getString()?.toBoolean() ?: true

        ForumLogger.info("Init database. impl: sql, LazyInit: $lazyInit")
        val url = config.propertyOrNull("database.sql.url")?.getString()
        val driver = config.propertyOrNull("database.sql.driver")?.getString()
        val user = config.propertyOrNull("database.sql.user")?.getString()
        val password = config.propertyOrNull("database.sql.password")?.getString()

        if (url == null || driver == null)
        {
            ForumLogger.severe("${RED}Database configuration not found.")
            ForumLogger.severe("${RED}Please add properties in application.conf:")
            ForumLogger.severe("${CYAN}database.sql.url${RESET}")
            ForumLogger.severe("${CYAN}database.sql.driver${RESET}")
            ForumLogger.severe("${CYAN}database.sql.user${GREEN} (optional)${RESET}")
            ForumLogger.severe("${CYAN}database.sql.password${GREEN} (optional)${RESET}")
            ForumLogger.severe("${CYAN}database.sql.lazyInit${GREEN} (optional, default = true)${RESET}")

            shutdown(1, "Database configuration not found.")
        }

        ForumLogger.info("Load database configuration. url: $url, driver: $driver, user: $user")
        val module = module(!lazyInit)
        {
            named("database")

            single {
                Database.connect(createHikariDataSource(url, driver, user, password))
            }.bind<Database>()

            singleOf(::BlocksImpl).bind<Blocks>()
            singleOf(::CommentsImpl).bind<Comments>()
            singleOf(::EmailCodesImpl).bind<EmailCodes>()
            singleOf(::LikesImpl).bind<Likes>()
            singleOf(::NoticesImpl).bind<Notices>()
            singleOf(::OperationsImpl).bind<Operations>()
            singleOf(::PermissionsImpl).bind<Permissions>()
            singleOf(::PostsImpl).bind<Posts>()
            singleOf(::PrivateChatsImpl).bind<PrivateChats>()
            singleOf(::ProhibitsImpl).bind<Prohibits>()
            singleOf(::ReportsImpl).bind<Reports>()
            singleOf(::StarsImpl).bind<Stars>()
            singleOf(::UsersImpl).bind<Users>()
            singleOf(::WhitelistsImpl).bind<Whitelists>()
        }
        getKoin().loadModules(listOf(module))

        if (!lazyInit)
        {
            ForumLogger.info("${CYAN}Using database implementation: ${RED}sql${CYAN}, and ${RED}lazyInit${CYAN} is ${GREEN}false.")
            ForumLogger.info("${CYAN}It may take a while to initialize the database. Please wait patiently.")

            (get<Blocks>() as DaoSqlImpl<*>).table
            (get<Comments>() as DaoSqlImpl<*>).table
            (get<EmailCodes>() as DaoSqlImpl<*>).table
            (get<Likes>() as DaoSqlImpl<*>).table
            (get<Notices>() as DaoSqlImpl<*>).table
            (get<Operations>() as DaoSqlImpl<*>).table
            (get<Permissions>() as DaoSqlImpl<*>).table
            (get<Posts>() as DaoSqlImpl<*>).table
            (get<PrivateChats>() as DaoSqlImpl<*>).table
            (get<Prohibits>() as DaoSqlImpl<*>).table
            (get<Reports>() as DaoSqlImpl<*>).table
            (get<Stars>() as DaoSqlImpl<*>).table
            (get<Users>() as DaoSqlImpl<*>).table
            (get<Whitelists>() as DaoSqlImpl<*>).table
        }
    }
}