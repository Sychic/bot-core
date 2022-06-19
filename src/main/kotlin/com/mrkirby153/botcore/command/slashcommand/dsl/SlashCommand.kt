package com.mrkirby153.botcore.command.slashcommand.dsl

import com.mrkirby153.botcore.command.slashcommand.dsl.types.AutocompleteEligible
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command


open class AbstractSlashCommand<A : Arguments>(
    private val arguments: (() -> A)?
) {
    var body: (Context<A>.() -> Unit)? = null
    lateinit var name: String
    lateinit var description: String

    fun args() = arguments?.invoke()

    fun execute(event: SlashCommandInteractionEvent) {
        val ctx = Context(this, event)
        ctx.load()
        body?.invoke(ctx)
    }

    fun handleAutocomplete(event: CommandAutoCompleteInteractionEvent): List<Command.Choice> {
        val argInst =
            args() ?: return listOf(Command.Choice("<<INVALID AUTOCOMPLETE SETTING>>", -1))
        val focused = event.focusedOption.name
        val inst = argInst.get(focused) ?: return emptyList()
        val builder = inst.builder
        if (builder is AutocompleteEligible) {
            return if (builder.autocompleteFunction != null) {
                builder.autocompleteFunction!!.invoke(event)
            } else {
                listOf(Command.Choice("<<NO AUTOCOMPLETE HANDLER>>", -1))
            }
        }
        return emptyList()
    }
}

@SlashDsl
class SlashCommand<A : Arguments>(
    arguments: (() -> A)? = null
) : AbstractSlashCommand<A>(arguments) {

    val subCommands = mutableMapOf<String, SubCommand<*>>()
    val groups = mutableMapOf<String, Group>()

    fun action(action: Context<A>.() -> Unit) {
        if (groups.isNotEmpty()) {
            throw IllegalArgumentException("Cannot mix groups and non-grouped commands")
        }
        this.body = action
    }

    override fun toString(): String {
        return buildString {
            appendLine("SlashCommand: $name")
            val arguments = args()
            if (arguments != null) {
                append("args: ${arguments.get().joinToString(",")}")
            }
        }
    }

    fun getSubCommand(name: String) = subCommands[name]
    fun getSubCommand(group: String, name: String) = groups[group]?.getCommand(name)

}

@SlashDsl
class Group(
    val name: String
) {
    val commands = mutableListOf<SubCommand<*>>()
    lateinit var description: String
    fun getCommand(name: String) = commands.firstOrNull { it.name == name }
}

@SlashDsl
class SubCommand<A : Arguments>(arguments: (() -> A)? = null) :
    AbstractSlashCommand<A>(arguments) {
    fun action(action: Context<A>.() -> Unit) {
        this.body = action
    }
}