package io.github.tamawish.pureeconomy.util

import java.util.Locale

/** Shared tab-completion helpers for PureEconomy commands. */
object CommandCompletions {
    /**
     * Returns values whose lowercase form starts with the given prefix.
     *
     * @param values candidate completions
     * @param prefix partial input from the player; may be empty
     * @return matching values in the same order as [values]
     */
    @JvmStatic
    fun filter(
        values: List<String>,
        prefix: String?,
    ): List<String> {
        val normalized = prefix?.lowercase(Locale.ROOT) ?: ""
        val matches = ArrayList<String>()
        for (value in values) {
            if (value.lowercase(Locale.ROOT).startsWith(normalized)) {
                matches.add(value)
            }
        }
        return matches
    }
}
