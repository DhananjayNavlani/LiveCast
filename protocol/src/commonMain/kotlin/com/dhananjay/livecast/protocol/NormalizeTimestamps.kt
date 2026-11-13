package com.dhananjay.livecast.protocol

/**
 * Recursively normalizes timestamp representations in maps and lists to epoch milliseconds (Long).
 * Values for which [toEpochMillis] returns non-null are converted; non-timestamps and nulls are untouched.
 */
fun normalizeTimestamps(
    data: Map<String, Any?>,
    toEpochMillis: (Any) -> Long?
): Map<String, Any?> {
    return data.mapValues { (_, value) ->
        normalizeValue(value, toEpochMillis)
    }
}

private fun normalizeValue(value: Any?, toEpochMillis: (Any) -> Long?): Any? {
    if (value == null) return null
    toEpochMillis(value)?.let { return it }
    return when (value) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            normalizeTimestamps(value as Map<String, Any?>, toEpochMillis)
        }
        is List<*> -> {
            value.map { normalizeValue(it, toEpochMillis) }
        }
        else -> value
    }
}
