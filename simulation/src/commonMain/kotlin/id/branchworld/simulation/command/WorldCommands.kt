package id.branchworld.simulation.command

internal val commandOrder = compareBy(WorldCommand::tick, WorldCommand::sequence)

/** Orders validated commands and retains their original keys for replay. */
internal class WorldCommands<C : WorldCommand>(private val recordHistory: Boolean = true) {
    private val pending = mutableMapOf<Long, MutableMap<Long, C>>()
    private val history = mutableListOf<C>()

    fun submit(tick: Long, command: C): Boolean {
        if (command.tick <= tick || command.sequence < 0) return false
        val atTick = pending.getOrPut(command.tick, ::mutableMapOf)
        if (command.sequence in atTick) return false
        atTick[command.sequence] = command
        if (recordHistory) history.add(command)
        return true
    }

    fun submitNext(tick: Long, command: (Long, Long) -> C): Boolean {
        if (tick == Long.MAX_VALUE) return false
        val nextTick = tick + 1
        val lastSequence = pending[nextTick]?.keys?.maxOrNull() ?: -1L
        if (lastSequence == Long.MAX_VALUE) return false
        return submit(tick, command(nextTick, lastSequence + 1))
    }

    fun cancel(command: C): Boolean {
        val atTick = pending[command.tick] ?: return false
        if (atTick[command.sequence] != command) return false
        atTick.remove(command.sequence)
        if (atTick.isEmpty()) pending.remove(command.tick)
        if (recordHistory) history.remove(command)
        return true
    }

    fun at(tick: Long): List<C> =
        pending.remove(tick)?.values?.sortedBy(WorldCommand::sequence).orEmpty()

    fun pending(): List<C> = pending.values.flatMap { it.values }.sortedWith(commandOrder)

    fun accepted(): List<C> = history.sortedWith(commandOrder)
}
