@file:Suppress("unused", "MemberVisibilityCanBePrivate")
package subit.console

import subit.console.AnsiStyle.Companion.ansi
import java.awt.Color
import java.util.*

enum class ColorDisplayMode
{
    /**
     * 不显示颜色
     */
    NONE,

    /**
     * 基础颜色
     */
    SIMPLE,

    /**
     * RGB颜色
     */
    RGB;
}

enum class EffectDisplayMode
{
    /**
     * 不显示效果
     */
    OFF,

    /**
     * 显示效果
     */
    ON
}

/**
 * 终端显示样式
 */
enum class AnsiEffect(val id: Int)
{
    /**
     * 加粗
     */
    BOLD(1),

    /**
     * 细体
     */
    THIN(2),

    /**
     * 斜体
     */
    ITALIC(3),

    /**
     * 下划线
     */
    UNDERLINE(4),

    /**
     * 闪烁
     */
    BLINK(5),

    /**
     * 快速闪烁
     */
    FAST_BLINK(6),

    /**
     * 反显
     */
    REVERSE(7),

    /**
     * 隐藏
     */
    HIDE(8),

    /**
     * 删除线
     */
    STRIKE(9);

    override fun toString() = this.ansi().toString()
}

interface AnsiColor
{
    val colorCode: String
    val isBackground: Boolean
    fun foreground(): AnsiColor
    fun background(): AnsiColor
    fun toSimpleColor(): SimpleAnsiColor
}

data class SimpleAnsiColor(private val color: Int = 0, private val code: Int = 30): AnsiColor
{
    override val colorCode: String
        get() = "${code+color}"
    override val isBackground: Boolean
        get() = code==BACKGROUND||code==BACKGROUND_BRIGHT

    companion object
    {
        const val BLACK_ID = 0
        const val RED_ID = 1
        const val GREEN_ID = 2
        const val YELLOW_ID = 3
        const val BLUE_ID = 4
        const val PURPLE_ID = 5
        const val CYAN_ID = 6
        const val WHITE_ID = 7
        const val FOREGROUND = 30
        const val FOREGROUND_BRIGHT = 90
        const val BACKGROUND = 40
        const val BACKGROUND_BRIGHT = 100
        val BLACK = SimpleAnsiColor(BLACK_ID, FOREGROUND)
        val RED = SimpleAnsiColor(RED_ID, FOREGROUND)
        val GREEN = SimpleAnsiColor(GREEN_ID, FOREGROUND)
        val YELLOW = SimpleAnsiColor(YELLOW_ID, FOREGROUND)
        val BLUE = SimpleAnsiColor(BLUE_ID, FOREGROUND)
        val PURPLE = SimpleAnsiColor(PURPLE_ID, FOREGROUND)
        val CYAN = SimpleAnsiColor(CYAN_ID, FOREGROUND)
        val WHITE = SimpleAnsiColor(WHITE_ID, FOREGROUND)
        val entries = mapOf(
            "black" to BLACK,
            "red" to RED,
            "green" to GREEN,
            "yellow" to YELLOW,
            "blue" to BLUE,
            "purple" to PURPLE,
            "cyan" to CYAN,
            "white" to WHITE
        )
    }

    fun bright() = if (code==FOREGROUND||code==BACKGROUND) SimpleAnsiColor(color, code+60) else this
    override fun background() = if (code==FOREGROUND||code==FOREGROUND_BRIGHT) SimpleAnsiColor(color, code+10) else this
    override fun toSimpleColor() = this
    fun unBright() = if (code==FOREGROUND_BRIGHT||code==BACKGROUND_BRIGHT) SimpleAnsiColor(color, code-60) else this
    override fun foreground() = if (code==BACKGROUND||code==BACKGROUND_BRIGHT) SimpleAnsiColor(color, code-10) else this
    override fun toString() = this.ansi().toString()
}

data class RGBAnsiColor(private val r: Int, private val g: Int, private val b: Int, override val isBackground: Boolean):
    AnsiColor
{
    override val colorCode: String
        get() = "${if (isBackground) 48 else 38};2;${r and 0xff};${g and 0xff};${b and 0xff}"

    companion object
    {
        fun fromRGB(r: Int, g: Int, b: Int) = RGBAnsiColor(r, g, b, false)
        fun fromHex(hex: Int) = RGBAnsiColor(hex shr 16 and 0xff, hex shr 8 and 0xff, hex and 0xff, false)
        fun fromHex(hex: String) = fromHex(hex.toInt(16))
        fun fromColor(color: Color) = RGBAnsiColor(color.red, color.green, color.blue, false)
        fun Color.toAnsiColor() = fromColor(this)
    }

    override fun background() = RGBAnsiColor(r, g, b, true)
    override fun toSimpleColor(): SimpleAnsiColor
    {
        val color = if (r > g && r > b && r-g-b > 100) SimpleAnsiColor.RED
        else if (g>r&&g>b&&g-r-b>100) SimpleAnsiColor.GREEN
        else if (b>r&&b>g&&b-r-g>100) SimpleAnsiColor.BLUE
        else if (r-b>100&&g-b>100) SimpleAnsiColor.YELLOW
        else if (r-g>100&&b-g>100) SimpleAnsiColor.PURPLE
        else if (g-r>100&&b-r>100) SimpleAnsiColor.CYAN
        else if (r+g+b>450) SimpleAnsiColor.WHITE
        else SimpleAnsiColor.BLACK
        return if (isBackground) color.background() else color.foreground()
    }

    override fun foreground() = RGBAnsiColor(r, g, b, false)
    override fun toString() = this.ansi().toString()
}

class AnsiStyle(
    foregroundColor: AnsiColor? = null,
    backgroundColor: AnsiColor? = null,
    vararg effects: AnsiEffect = arrayOf()
)
{
    companion object
    {
        val RESET = object: Any()
        {
            override fun equals(other: Any?): Boolean = other===this
            override fun hashCode(): Int = 0
            override fun toString(): String =
                if (Console.ansiEffectMode==EffectDisplayMode.ON||Console.ansiColorMode!=ColorDisplayMode.NONE) "\u001B[0m" else ""
        }
        val empty = AnsiStyle()
        fun AnsiColor.ansi() =
            if (this.isBackground) AnsiStyle(backgroundColor = this) else AnsiStyle(foregroundColor = this)

        fun AnsiColor.ansi(vararg effects: AnsiEffect) = ansi().apply { this.effects.addAll(effects) }
        fun AnsiColor.ansi(effects: Collection<AnsiEffect>) = ansi().apply { this.effects.addAll(effects) }
        fun AnsiColor.ansi(effects: EnumSet<AnsiEffect>) = ansi().apply { this.effects.addAll(effects) }
        fun AnsiEffect.ansi() = AnsiStyle(effects = arrayOf(this))
    }

    var foregroundColor = foregroundColor?.foreground()
    var backgroundColor = backgroundColor?.background()
    var effects: EnumSet<AnsiEffect> = EnumSet.noneOf(AnsiEffect::class.java)

    init
    {
        this.effects.addAll(effects)
    }

    override fun equals(other: Any?): Boolean
    {
        if (this===other) return true
        if (other==null||javaClass!=other.javaClass) return false
        other as AnsiStyle
        if (foregroundColor!=other.foregroundColor) return false
        if (backgroundColor!=other.backgroundColor) return false
        if (effects!=other.effects) return false
        return true
    }

    override fun hashCode(): Int
    {
        var result = foregroundColor.hashCode()
        result = 31*result+backgroundColor.hashCode()
        result = 31*result+effects.hashCode()
        return result
    }

    private fun needDisplayStyle() =
        AnsiStyle(
            foregroundColor = when (Console.ansiColorMode)
            {
                ColorDisplayMode.RGB    -> foregroundColor
                ColorDisplayMode.SIMPLE -> foregroundColor?.toSimpleColor()
                else                    -> null
            },
            backgroundColor = when (Console.ansiColorMode)
            {
                ColorDisplayMode.RGB    -> backgroundColor
                ColorDisplayMode.SIMPLE -> backgroundColor?.toSimpleColor()
                else                    -> null
            },
            effects = if (Console.ansiEffectMode==EffectDisplayMode.ON) effects.toTypedArray() else arrayOf()
        )

    fun forceToString(): String
    {
        if (effects.isEmpty()&&foregroundColor==null&&backgroundColor==null) return ""
        val sb = StringBuilder()
        sb.append("\u001B[")
        effects.forEach { sb.append(it.id).append(';') }
        if (foregroundColor!=null) sb.append(foregroundColor?.colorCode).append(';')
        if (backgroundColor!=null) sb.append(backgroundColor?.colorCode).append(';')
        sb.deleteCharAt(sb.length-1)
        sb.append('m')
        return sb.toString()
    }

    override fun toString() = needDisplayStyle().forceToString()
    operator fun plusAssign(other: AnsiStyle)
    {
        effects.addAll(other.effects)
        if (other.foregroundColor!=null) foregroundColor = other.foregroundColor
        if (other.backgroundColor!=null) backgroundColor = other.backgroundColor
    }

    operator fun plusAssign(other: AnsiEffect)
    {
        effects.add(other)
    }

    operator fun plus(other: AnsiStyle): AnsiStyle
    {
        val style = AnsiStyle(foregroundColor, backgroundColor, *effects.toTypedArray())
        style += other
        return style
    }

    operator fun plus(other: AnsiEffect): AnsiStyle
    {
        val style = AnsiStyle(foregroundColor, backgroundColor, *effects.toTypedArray())
        style += other
        return style
    }

    operator fun minusAssign(other: AnsiStyle)
    {
        effects.removeAll(other.effects)
        if (other.foregroundColor!=null&&foregroundColor==other.foregroundColor) foregroundColor = null
        if (other.backgroundColor!=null&&backgroundColor==other.backgroundColor) backgroundColor = null
    }

    operator fun minusAssign(other: AnsiEffect)
    {
        effects.remove(other)
    }

    operator fun minus(other: AnsiStyle): AnsiStyle
    {
        val style = AnsiStyle(foregroundColor, backgroundColor, *effects.toTypedArray())
        style -= other
        return style
    }

    operator fun minus(other: AnsiEffect): AnsiStyle
    {
        val style = AnsiStyle(foregroundColor, backgroundColor, *effects.toTypedArray())
        style -= other
        return style
    }
}