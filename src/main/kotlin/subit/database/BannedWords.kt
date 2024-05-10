package subit.database

import subit.dataClasses.Slice

interface BannedWords
{
    suspend fun addBannedWord(word: String)
    suspend fun removeBannedWord(word: String)
    suspend fun updateBannedWord(oldWord: String, newWord: String)
    suspend fun getBannedWords(begin: Long, count: Int): Slice<String>
}