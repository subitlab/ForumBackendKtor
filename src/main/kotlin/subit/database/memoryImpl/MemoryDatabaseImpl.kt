package subit.database.memoryImpl

import io.ktor.server.application.*
import org.koin.core.component.KoinComponent
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import subit.console.SimpleAnsiColor.Companion.GREEN
import subit.console.SimpleAnsiColor.Companion.RED
import subit.console.SimpleAnsiColor.Companion.YELLOW
import subit.database.*
import subit.debug
import subit.logger.ForumLogger
import subit.utils.Power

object MemoryDatabaseImpl: IDatabase, KoinComponent
{
    override val name: String = "memory"

    override fun Application.init()
    {
        val logger = ForumLogger.getLogger()
        logger.info("Init database. impl: $name")

        if (!debug)
        {
            logger.severe("${GREEN}MemoryDatabaseImpl${RED} 只应当于debug模式使用.")
            logger.severe("${RED}开启debug模式请在启动参数中加入 -debug=true")
            logger.severe("${RED}或将配置文件中的数据库实现改为其他实现")
            logger.severe("${RED}程序即将退出.")
            Power.shutdown(1, "MemoryDatabaseImpl only for debug mode.")
        }

        logger.warning("${YELLOW}注意: 您正在使用${RED}内存数据库实现${YELLOW}.")
        logger.warning("${YELLOW}该实现所有数据将保存于内存中, 当程序退出时数据将${RED}全部丢失${YELLOW}.")
        logger.warning("${YELLOW}该实现仅适用于数据量较小的${RED}测试环境${YELLOW}, 请勿在生产环境中使用该实现.")

        val module = module()
        {
            named("memory-database-impl")

            singleOf(::BannedWordsImpl).bind<BannedWords>()
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
    }
}