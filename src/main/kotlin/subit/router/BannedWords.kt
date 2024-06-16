@file:Suppress("PackageDirectoryMismatch")

package subit.router.bannedWords

import io.github.smiley4.ktorswaggerui.dsl.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import subit.database.BannedWords
import subit.database.checkPermission
import subit.database.receiveAndCheckBody
import subit.router.*
import subit.utils.HttpStatus
import subit.utils.respond
import subit.utils.statuses

fun Route.bannedWords()
{
    route("/bannedWord", {
        tags = listOf("违禁词汇")
    })
    {
        get("/list", {
            description = "获取违禁词汇列表, 需要全局管理员"
            request {
                authenticated(true)
                paged()
            }
            response {
                statuses<List<String>>(HttpStatus.OK, example = listOf("违禁词汇1", "违禁词汇2", "违禁词汇3"))
                statuses(HttpStatus.Forbidden, HttpStatus.Unauthorized)
            }
        }) { getBannedWords() }

        post("/new", {
            description = "添加违禁词汇, 需要全局管理员"
            request {
                authenticated(true)
                body<NewBannedWord>
                {
                    required = true
                    description = "新违禁词汇"
                    example("example", NewBannedWord("违禁词汇"))
                }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
            }
        }) { newBannedWord() }

        delete("/{word}", {
            description = "删除违禁词汇, 需要全局管理员"
            request {
                authenticated(true)
                pathParameter<String>("word")
                {
                    required = true
                    description = "违禁词汇"
                    example = "违禁词汇"
                }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
            }
        }) { deleteBannedWord() }

        put("/{word}", {
            description = "修改违禁词汇, 需要全局管理员"
            request {
                authenticated(true)
                pathParameter<String>("word")
                {
                    required = true
                    description = "违禁词汇"
                    example = "违禁词汇"
                }
                body<NewBannedWord>
                {
                    required = true
                    description = "新违禁词汇"
                    example("example", NewBannedWord("违禁词汇"))
                }
            }
            response {
                statuses(HttpStatus.OK, HttpStatus.Forbidden, HttpStatus.Unauthorized)
            }
        }) { editBannedWord() }
    }
}

private suspend fun Context.getBannedWords()
{
    val (begin, count) = call.getPage()
    val bannedWords = get<BannedWords>()
    checkPermission { checkHasGlobalAdmin() }
    call.respond(HttpStatus.OK, bannedWords.getBannedWords(begin, count))
}

@Serializable
private data class NewBannedWord(val word: String)

private suspend fun Context.newBannedWord()
{
    val newBannedWord = receiveAndCheckBody<NewBannedWord>()
    val bannedWords = get<BannedWords>()
    checkPermission { checkHasGlobalAdmin() }
    bannedWords.addBannedWord(newBannedWord.word)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.deleteBannedWord()
{
    val word = call.parameters["word"] ?: return call.respond(HttpStatus.BadRequest)
    val bannedWords = get<BannedWords>()
    checkPermission { checkHasGlobalAdmin() }
    bannedWords.removeBannedWord(word)
    call.respond(HttpStatus.OK)
}

private suspend fun Context.editBannedWord()
{
    val word = call.parameters["word"] ?: return call.respond(HttpStatus.BadRequest)
    val newBannedWord = receiveAndCheckBody<NewBannedWord>()
    val bannedWords = get<BannedWords>()
    checkPermission { checkHasGlobalAdmin() }
    bannedWords.updateBannedWord(word, newBannedWord.word)
    call.respond(HttpStatus.OK)
}