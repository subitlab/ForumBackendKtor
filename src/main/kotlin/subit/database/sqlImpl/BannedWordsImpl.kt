package subit.database.sqlImpl

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.BannedWords

class BannedWordsImpl: BannedWords, DaoSqlImpl<BannedWordsImpl.BannedWordsTable>(BannedWordsTable)
{
    object BannedWordsTable: IdTable<String>("banned_words")
    {
        val word = varchar("word", 255).entityId()
        override val id = word
        override val primaryKey = PrimaryKey(word)
    }

    override suspend fun addBannedWord(word: String): Unit = query()
    {
        insert { it[this.word] = word }
    }
    override suspend fun removeBannedWord(word: String): Unit = query()
    {
        deleteWhere { table.word eq word }
    }
    override suspend fun updateBannedWord(oldWord: String, newWord: String): Unit = query()
    {
        update({ table.word eq oldWord }) { it[word] = newWord }
    }
    override suspend fun getBannedWords(begin: Long, count: Int): Slice<String> = query()
    {
        selectAll().asSlice(begin,count).map { it[word].value }
    }
    override suspend fun check(str: String): Boolean = query()
    {
        selectAll().fetchBatchedResults().any { it.any { row -> str.contains(row[word].value) } }
    }
}