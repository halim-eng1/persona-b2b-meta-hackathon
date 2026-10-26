package com.example.persona.core.database.entity

/**
 * Domain enum enforcing the 6 allowed lifecycle states for an order row
 * in [orders_table].
 *
 * Persisted as a TEXT column via the [com.example.persona.core.database.Converters]
 * TypeConverter pair. Any unknown or corrupted string read from the database
 * falls back safely to [PENDING] without throwing.
 *
 * Terminal states (order lifecycle ended): [SHIPPED], [CANCELLED], [RETURNED].
 * Active/unfulfilled states (deduplication check scope): [PENDING], [IN_PREPARATION].
 */
enum class OrderStatus {
    PENDING,
    IN_PREPARATION,
    SHIPPING_IN_PROGRESS,
    SHIPPED,
    CANCELLED,
    RETURNED;

    companion object {
        /**
         * Safe deserializer. Returns [PENDING] for any null, blank, or
         * unrecognised string value — never throws.
         */
        fun fromString(value: String?): OrderStatus {
            if (value.isNullOrBlank()) return PENDING
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: PENDING
        }

        /** Convenience set for active (non-terminal) status checks. */
        val ACTIVE_STATES: Set<OrderStatus> = setOf(PENDING, IN_PREPARATION, SHIPPING_IN_PROGRESS)

        /** Terminal states — order lifecycle has ended. */
        val TERMINAL_STATES: Set<OrderStatus> = setOf(SHIPPED, CANCELLED, RETURNED)

        /** Unfulfilled states used in the deduplication verification window. */
        val UNFULFILLED_STATES: Set<OrderStatus> = setOf(PENDING, IN_PREPARATION)

        /**
         * Pre-computed names of [UNFULFILLED_STATES], for `status`/`in`-matches
         * against the persisted TEXT column. Precomputing avoids allocating a
         * fresh list per order per ui-state emission on the dashboard path.
         */
        val UNFULFILLED_STATE_NAMES: Set<String> = UNFULFILLED_STATES.map { it.name }.toSet()
    }
}
