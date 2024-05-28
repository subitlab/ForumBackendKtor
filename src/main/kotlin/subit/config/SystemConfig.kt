package subit.config

import kotlinx.serialization.Serializable
import net.mamoe.yamlkt.Comment

@Serializable
data class SystemConfig(
    @Comment("是否在系统维护中")
    val systemMaintaining: Boolean
)

var systemConfig: SystemConfig by config("system.yml", SystemConfig(false))