package com.mrkirby153.botcore.command.slashcommand.dsl

import com.mrkirby153.botcore.command.slashcommand.dsl.types.AutocompleteEligible
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.CommandPermissions


/**
 * The top-level slash command object from which both [SlashCommands][SlashCommand] and
 * [SubCommands][SubCommand] inherit from.
 *
 * All slash commands have a [name] and [description] which must be set before the command is
 * committed to discord. In addition, [arguments] may be provided to identify the arguments for
 * the slash command
 *
 * @param arguments A function returning a new instance of the class to use for arguments. Arguments
 * are exposed under the `args` variable during execution
 * @param A An arguments class from which slash command arguments are derived from
 * @see [slashCommand]
 */
open class AbstractSlashCommand<A : Arguments>(
    private val arguments: (() -> A)?
) {
    var body: (SlashContext<A>.() -> Unit)? = null
    lateinit var name: String
    lateinit var description: String

    /**
     * Returns a new instance of the arguments class
     */
    fun args() = arguments?.invoke()

    /**
     * Executes the slash command. If [body] is null, this no-ops
     */
    fun execute(event: SlashCommandInteractionEvent) {
        val ctx = SlashContext(this, event)
        ctx.load()
        body?.invoke(ctx)
    }

    /**
     * Handle the autocomplete [event]
     * @return The list of choices derived from the commands autocomplete function, or an empty list
     * if the command is not autocompletable
     *
     * @see AutocompleteEligible
     */
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

/**
 * A top level slash command.
 *
 * Slash commands can either have sub-command groups or sub-commands, but not both.
 *
 * The following is how sub-commands and groups relate
 * * `/command subCommand`
 * * `/command group subCommand`
 */
@SlashDsl
class SlashCommand<A : Arguments>(
    arguments: (() -> A)? = null
) : AbstractSlashCommand<A>(arguments) {

    val subCommands = mutableMapOf<String, SubCommand<*>>()
    val groups = mutableMapOf<String, Group>()
    internal var commandPermissions = CommandPermissions.ENABLED

    /**
     * Defines the action run when this slash command is invoked
     */
    fun action(action: SlashContext<A>.() -> Unit) {
        if (groups.isNotEmpty()) {
            throw IllegalArgumentException("Cannot mix groups and non-grouped commands")
        }
        this.body = action
    }

    /**
     * Get a sub command by its [name]
     */
    fun getSubCommand(name: String) = subCommands[name]

    /**
     * Get a sub command by its [group] and [name]
     */
    fun getSubCommand(group: String, name: String) = groups[group]?.getCommand(name)

    /**
     * Sets the default [permissions] for this command.
     *
     * If a user does not have these permissions, the command will not show in the client.
     */
    fun defaultPermissions(vararg permissions: Permission) {
        commandPermissions = CommandPermissions.enabledFor(*permissions)
    }

    /**
     * Disable this command by default. Server administrators will need to enable it manually
     */
    fun disabledByDefault() {
        commandPermissions = CommandPermissions.DISABLED
    }

}

/**
 * Wrapper object for command groups
 *
 * @param name The name of the group
 */
@SlashDsl
class Group(
    val name: String
) {
    val commands = mutableListOf<SubCommand<*>>()
    lateinit var description: String
    fun getCommand(name: String) = commands.firstOrNull { it.name == name }
}

/**
 * Object for sub commands. Behaves identically to [SlashCommand] except it does not allow sub-commands
 * or groups.
 */
@SlashDsl
class SubCommand<A : Arguments>(arguments: (() -> A)? = null) :
    AbstractSlashCommand<A>(arguments) {

    /**
     * The action that this sub-command will run when invoked
     */
    fun action(action: SlashContext<A>.() -> Unit) {
        this.body = action
    }
}