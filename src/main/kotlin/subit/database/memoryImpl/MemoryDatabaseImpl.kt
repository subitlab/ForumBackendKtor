package subit.database.memoryImpl

import io.ktor.server.application.*
import org.koin.core.component.KoinComponent
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import subit.console.SimpleAnsiColor.Companion.RED
import subit.console.SimpleAnsiColor.Companion.YELLOW
import subit.database.*
import subit.logger.ForumLogger

object MemoryDatabaseImpl: IDatabase, KoinComponent
{
    override val name: String = "memory"

    override fun Application.init()
    {
        val logger = ForumLogger.getLogger()
        logger.info("Init database. impl: $name")
        logger.warning("${YELLOW}注意: 您正在使用${RED}内存数据库实现${YELLOW}.")
        logger.warning("${YELLOW}该实现所有数据将保存于内存中, 当程序退出时数据将${RED}全部丢失${YELLOW}.")
        logger.warning("${YELLOW}该实现仅适用于数据量较小的${RED}测试环境${YELLOW}, 请勿在生产环境中使用该实现.")

        val module = module()
        {
            named("database")

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