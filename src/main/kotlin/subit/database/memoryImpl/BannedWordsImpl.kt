package subit.database.memoryImpl

import subit.dataClasses.Slice
import subit.dataClasses.Slice.Companion.asSlice
import subit.database.BannedWords
import java.util.Collections

class BannedWordsImpl: BannedWords
{
    private val set = Collections.synchronizedSet(hashSetOf<String>())
    override suspend fun addBannedWord(word: String)
    {
        set.add(word)
    }
    override suspend fun removeBannedWord(word: String)
    {
        set.remove(word)
    }
    override suspend fun updateBannedWord(oldWord: String, newWord: String)
    {
        if (set.remove(oldWord)) set.add(newWord)
    }
    override suspend fun getBannedWords(begin: Long, count: Int): Slice<String>
    {
        return set.toList().asSlice(begin, count)
    }
}