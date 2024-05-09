@file:Suppress("PackageDirectoryMismatch")

package subit.router.rss

import io.github.smiley4.ktorswaggerui.dsl.get
import io.ktor.server.routing.*

fun Route.rss()
{
    get("rss", {
        description = "RSS订阅接口"
    })
    {

    }
}