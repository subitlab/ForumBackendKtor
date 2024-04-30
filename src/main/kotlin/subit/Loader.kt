package subit

import subit.logger.ForumLogger
import java.io.InputStream

object Loader
{
    /**
     * 获取资源文件
     * @param path 资源路径
     * @return 资源文件输入流
     */
    fun getResource(path: String): InputStream?
    {
        if (path.startsWith("/")) return Loader::class.java.getResource(path)?.openStream()
        return Loader::class.java.getResource("/$path")?.openStream()
    }
}