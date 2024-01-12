package subit.console.command

import subit.console.*
import subit.console.AnsiStyle.Companion.ansi

object Color: TreeCommand(Test, Mode, Effect)
{
    override val description = "color settings"

    object Test: Command
    {
        override val description = "Test color display. If you appear garbled, you can adjust the color settings."
        override fun execute(args: List<String>): Boolean
        {
            val sb = StringBuilder().append("")
                .append("If certain colors or effects are not supported, ")
                .append("you can use ")
                .append(AnsiEffect.BOLD.ansi()+AnsiEffect.ITALIC)
                .append("color mode [rgb | simple | none] ")
                .append(AnsiStyle.RESET)
                .append("and ")
                .append(AnsiEffect.BOLD.ansi()+AnsiEffect.ITALIC)
                .append("color effect [on | off]")
                .append(AnsiStyle.RESET)
                .append("to set the corresponding color and effect modes, respectively")
                .append("\n")
            for (effect in AnsiEffect.entries) sb.append("$effect${effect.name.lowercase()}${AnsiStyle.RESET} ")
            sb.append("\n")
            for (color in SimpleAnsiColor.entries) sb.append("${color.value}${color.key}${AnsiStyle.RESET} ")
            sb.append("\n")
            sb.append("R:\n")
            for (i in 0..255) sb.append("${RGBAnsiColor.fromRGB(i, 0, 0)}$i${AnsiStyle.RESET} ")
            sb.append("\n")
            sb.append("G:\n")
            for (i in 0..255) sb.append("${RGBAnsiColor.fromRGB(0, i, 0)}$i${AnsiStyle.RESET} ")
            sb.append("\n")
            sb.append("B:\n")
            for (i in 0..255) sb.append("${RGBAnsiColor.fromRGB(0, 0, i)}$i${AnsiStyle.RESET} ")
            sb.toString().split("\n").forEach { CommandSet.out.println(it) }
            return true
        }
    }

    object Mode: TreeCommand(RGB, Simple, None)
    {
        override val description = "color display mode"

        object RGB: Command
        {
            override val description = "Use RGB and simple color"
            override fun execute(args: List<String>): Boolean
            {
                Console.ansiColorMode = ColorDisplayMode.RGB
                CommandSet.out.println("Color mode: RGB")
                return true
            }
        }

        object Simple: Command
        {
            override val description = "Use simple color"
            override fun execute(args: List<String>): Boolean
            {
                Console.ansiColorMode = ColorDisplayMode.SIMPLE
                CommandSet.out.println("Color mode: Simple")
                return true
            }
        }

        object None: Command
        {
            override val description = "Disable color"
            override fun execute(args: List<String>): Boolean
            {
                Console.ansiColorMode = ColorDisplayMode.NONE
                CommandSet.out.println("Color mode: None")
                return true
            }
        }
    }

    object Effect: TreeCommand(On, Off)
    {
        override val description = "color effect"

        object On: Command
        {
            override val description = "Enable color effect"
            override fun execute(args: List<String>): Boolean
            {
                Console.ansiEffectMode = EffectDisplayMode.ON
                CommandSet.out.println("Color effect: On")
                return true
            }
        }

        object Off: Command
        {
            override val description = "Disable color effect"
            override fun execute(args: List<String>): Boolean
            {
                Console.ansiEffectMode = EffectDisplayMode.OFF
                CommandSet.out.println("Color effect: Off")
                return true
            }
        }
    }
}