package com.areslib.pathing

import com.areslib.sequencer.Task

object NamedCommands {
    private val registry = mutableMapOf<String, (Long) -> Task>()

    /**
     * Registers a command builder by name.
     * The builder lambda takes a base timestamp (reference timestamp) and returns a [Task].
     */
    fun registerCommand(name: String, builder: (Long) -> Task) {
        registry[name] = builder
    }

    /**
     * Registers a constant static command by name.
     */
    fun registerCommand(name: String, task: Task) {
        registry[name] = { task }
    }

    /**
     * Resolves a registered command by name. Returns null if not found.
     */
    fun getCommand(name: String, timestampMs: Long): Task? {
        val builder = registry[name] ?: return null
        return builder(timestampMs)
    }

    /**
     * Clears all registered commands.
     */
    fun clear() {
        registry.clear()
    }
}
