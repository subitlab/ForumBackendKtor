package subit.plugin

import io.ktor.server.application.*
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * 安装Koin依赖注入
 */
fun Application.installKoin() = install(Koin)
{
    slf4jLogger()
    modules(module {
        single { this@installKoin } bind Application::class
    })
}