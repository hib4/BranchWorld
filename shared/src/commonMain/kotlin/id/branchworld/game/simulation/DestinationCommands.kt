package id.branchworld.game.simulation

/** Orders already validated inputs and retains their original keys for replay. */
internal class DestinationCommands {
    private val pending = mutableMapOf<Long, MutableMap<Long, SetDestination>>()
    private val history = mutableListOf<SetDestination>()

    fun submit(tick: Long, command: SetDestination): Boolean {
        if (command.tick <= tick || command.sequence < 0) return false
        val atTick = pending.getOrPut(command.tick, ::mutableMapOf)
        if (command.sequence in atTick) return false
        atTick[command.sequence] = command
        history.add(command)
        return true
    }

    fun submitNext(tick: Long, entityId: Long, cell: GridCell): Boolean {
        if (tick == Long.MAX_VALUE) return false
        val nextTick = tick + 1
        val lastSequence = pending[nextTick]?.keys?.maxOrNull() ?: -1L
        if (lastSequence == Long.MAX_VALUE) return false
        return submit(tick, SetDestination(nextTick, lastSequence + 1, entityId, cell))
    }

    fun at(tick: Long): List<SetDestination> =
        pending.remove(tick)?.values?.sortedBy(SetDestination::sequence).orEmpty()

    fun accepted(): List<SetDestination> =
        history.sortedWith(compareBy(SetDestination::tick, SetDestination::sequence))
}
