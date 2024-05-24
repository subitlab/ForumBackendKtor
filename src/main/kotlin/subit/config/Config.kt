package subit.config

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlElement
import net.mamoe.yamlkt.YamlMap
import subit.logger.ForumLogger
import subit.workDir
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 *
 */
inline fun <reified T: Any> config(
    filename: String,
    default: T,
    vararg listeners: (T, T)->Unit
): ConfigLoader<T> =
    ConfigLoader.createLoader(filename, default, typeOf<T>(), *listeners)

class ConfigLoader<T: Any> private constructor(
    private val default: T,
    private val filename: String,
    private val type: KType,
    private var listeners: MutableSet<(T, T)->Unit> = mutableSetOf()
)
{
    @Suppress("UNCHECKED_CAST")
    private var config: T = getConfigOrCreate(filename, default as Any, type) as T

    private fun setValue(value: T)
    {
        listeners.forEach {
            logger.severe("Error in config listener")
            {
                it(config, value)
            }
        }
        config = value
        logger.config("Config $filename changed to $value")
        saveConfig(filename, value, type)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = config
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = setValue(value)

    @Suppress("UNCHECKED_CAST")
    fun reload()
    {
        logger.config("Reloading config $filename")
        logger.severe("Could not reload config $filename")
        {
            setValue(getConfigOrCreate(filename, default as Any, type) as T)
        }
    }

    @Suppress("unused", "MemberVisibilityCanBePrivate")
    companion object
    {
        private val logger = ForumLogger.getLogger()
        fun init() // 初始化所有配置
        {
            apiDocsConfig
            emailConfig
            filesConfig
            loggerConfig
        }

        /**
         * [WeakReference] 采用弱引用, 避免不被回收
         * @author nullaqua
         */
        private val configMap: MutableMap<String, WeakReference<ConfigLoader<*>>> =
            Collections.synchronizedMap(HashMap())

        fun configs() = configMap.keys
        fun reload(name: String) = configMap[name]?.let {
            it.get()?.apply { reload() } ?: configMap.remove(name)
        }
        fun reloadAll() = configMap.keys.forEach(::reload)

        private fun addLoader(loader: ConfigLoader<*>)
        {
            val r = configMap[loader.filename]
            if (r?.get() != null) error("Loader already exists")
            else if (r != null) configMap.remove(loader.filename)
            configMap[loader.filename] = WeakReference(loader)
        }

        fun <T: Any> createLoader(filename: String, default: T, type: KType, vararg listeners: (T, T)->Unit) =
            ConfigLoader(default, filename, type, listeners.toHashSet()).also(::addLoader)

        /// 配置文件加载 ///

        const val CONFIG_DIR = "configs"

        /**
         * 从配置文件中获取配置, 需要T是可序列化的.在读取失败时抛出错误
         * @param filename 配置文件名
         * @return 配置
         */
        inline fun <reified T> getConfig(filename: String): T =
            Yaml.decodeFromString(getConfigFile(filename).readText())

        fun getConfig(filename: String, type: KType): Any? =
            Yaml.decodeFromString(serializer(type), getConfigFile(filename).readText())

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时抛出错误
         * @param filename 配置文件名
         * @param path 配置路径
         * @return 配置
         */
        inline fun <reified T> getConfig(filename: String, path: String): T
        {
            val m = Yaml.decodeYamlMapFromString(getConfigFile(filename).readText())
            var e: YamlElement = m
            path.split(".").forEach {
                val map = e as? YamlMap ?: throw IllegalArgumentException("path $path not found")
                e = map[it] ?: throw IllegalArgumentException("path $path not found")
            }
            return Yaml.decodeFromString(e.toString())
        }

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回默认值
         * @param filename 配置文件名
         * @param default 默认值
         * @return 配置
         */
        inline fun <reified T> getConfig(filename: String, default: T): T =
            runCatching { getConfig<T>(filename) }.getOrDefault(default)

        @Suppress("UNCHECKED_CAST")
        fun <T> getConfig(filename: String, default: T, type: KType): T =
            runCatching { getConfig(filename, type) }.getOrDefault(default) as T

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
         * @param filename 配置文件名
         * @param path 配置路径
         * @param default 默认值
         * @return 配置
         */
        inline fun <reified T> getConfig(filename: String, path: String, default: T): T =
            runCatching { getConfig<T>(filename, path) }.getOrDefault(default)

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
         * @param filename 配置文件名
         * @return 配置
         */
        inline fun <reified T> getConfigOrNull(filename: String): T? = getConfig(filename, null)
        fun getConfigOrNull(filename: String, type: KType): Any? = getConfig(filename, null, type)

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回null
         * @param filename 配置文件名
         * @param path 配置路径
         * @return 配置
         */
        inline fun <reified T> getConfigOrNull(filename: String, path: String): T? = getConfig(filename, path, null)

        /**
         * 从配置文件中获取配置, 需要T是可序列化的,在读取失败时返回默认值,并将默认值写入配置文件
         * @param filename 配置文件名
         * @param default 默认值
         * @return 配置
         */
        inline fun <reified T> getConfigOrCreate(filename: String, default: T): T =
            getConfigOrNull<T>(filename) ?: default.also { saveConfig(filename, it) }

        fun getConfigOrCreate(filename: String, default: Any, type: KType): Any =
            getConfigOrNull(filename, type) ?: default.also { saveConfig(filename, default, type) }

        /**
         * 保存配置到文件
         * @param filename 配置文件名
         * @param config 配置
         */
        inline fun <reified T> saveConfig(filename: String, config: T) =
            createConfigFile(filename).writeText(Yaml.encodeToString(config))

        fun saveConfig(filename: String, config: Any, type: KType) =
            createConfigFile(filename).writeText(Yaml.encodeToString(serializer(type), config))

        /**
         * 获取配置文件
         * @param filename 配置文件名
         * @return 配置文件
         */
        fun getConfigFile(filename: String): File = File(File(workDir, CONFIG_DIR), filename)

        /**
         * 获取配置文件, 如果不存在则创建
         * @param filename 配置文件名
         * @return 配置文件
         */
        fun createConfigFile(filename: String): File =
            getConfigFile(filename).also { it.parentFile.mkdirs(); it.createNewFile() }
    }
}