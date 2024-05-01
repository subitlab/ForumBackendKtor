@file:Suppress("PackageDirectoryMismatch")
package subit.router.notice

import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.routing.*

fun Route.notice()
{
    route("/notice", {
        tags = listOf("通知")
    })
    {
        // TODO 获取通知列表
        get("/list")
        {
            // TODO
        }

        // TODO 获取通知详情
        get("/detail/{id}")
        {
            // TODO
        }

        // TODO 删除通知(已读)
        delete("/delete/{id}")
        {
            // TODO
        }
    }
}