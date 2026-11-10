package org.mapdb

/**
 * Primitive long -> object hash map (open addressing, no java.util.function).
 *
 * Android-safe replacement for org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap.
 */
class LongObjectHashMap<V>(initialCapacity: Int = 16) {

    private var keys: LongArray
    private var values: Array<Any?>
    private var state: ByteArray
    private var count: Int = 0
    private var deleted: Int = 0

    init {
        var capacity = 16
        while (capacity < initialCapacity * 2) {
            capacity = capacity shl 1
        }
        keys = LongArray(capacity)
        values = arrayOfNulls(capacity)
        state = ByteArray(capacity)
    }

    val size: Int
        get() = count

    val isEmpty: Boolean
        get() = count == 0

    @Suppress("UNCHECKED_CAST")
    fun get(key: Long): V? {
        val pos = indexOf(key)
        return if (pos < 0) null else values[pos] as V?
    }

    fun put(key: Long, value: V): V? {
        if ((count + deleted + 1) * 3 >= keys.size * 2) {
            rehash(keys.size * 2)
        }
        val mask = keys.size - 1
        var pos = hash(key) and mask
        var firstDeleted = -1
        while (true) {
            when (state[pos]) {
                EMPTY -> {
                    if (firstDeleted >= 0) {
                        pos = firstDeleted
                        deleted--
                    }
                    keys[pos] = key
                    values[pos] = value
                    state[pos] = USED.toByte()
                    count++
                    return null
                }
                USED -> if (keys[pos] == key) {
                    @Suppress("UNCHECKED_CAST")
                    val old = values[pos] as V?
                    values[pos] = value
                    return old
                }
                else -> if (firstDeleted < 0) {
                    firstDeleted = pos
                }
            }
            pos = (pos + 1) and mask
        }
    }

    fun containsKey(key: Long): Boolean = indexOf(key) >= 0

    @Suppress("UNCHECKED_CAST")
    fun remove(key: Long): V? {
        val pos = indexOf(key)
        if (pos < 0) {
            return null
        }
        val old = values[pos] as V?
        values[pos] = null
        state[pos] = DELETED.toByte()
        count--
        deleted++
        return old
    }

    fun clear() {
        keys = LongArray(16)
        values = arrayOfNulls(16)
        state = ByteArray(16)
        count = 0
        deleted = 0
    }

    /** Rebuilds the table, dropping deleted slots and unused capacity. */
    fun compact() {
        var capacity = 16
        while (capacity < count * 2) {
            capacity = capacity shl 1
        }
        rehash(capacity)
    }

    fun keySet(): LongSet = object : LongSet {
        override val size: Int
            get() = count

        override val isEmpty: Boolean
            get() = count == 0

        override fun contains(value: Long): Boolean = containsKey(value)

        override fun longIterator(): LongIterator = object : LongIterator() {
            private var pos = 0
            private var nextPos = -1

            override fun hasNext(): Boolean {
                if (nextPos >= 0) {
                    return true
                }
                while (pos < state.size) {
                    if (state[pos] == USED) {
                        nextPos = pos
                        return true
                    }
                    pos++
                }
                return false
            }

            override fun nextLong(): Long {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                val result = keys[nextPos]
                pos = nextPos + 1
                nextPos = -1
                return result
            }
        }
    }

    fun forEachKeyValue(action: (Long, V) -> Unit) {
        for (i in state.indices) {
            if (state[i] == USED) {
                @Suppress("UNCHECKED_CAST")
                action(keys[i], values[i] as V)
            }
        }
    }

    private fun indexOf(key: Long): Int {
        val mask = keys.size - 1
        var pos = hash(key) and mask
        while (true) {
            when (state[pos]) {
                EMPTY -> return -1
                USED -> if (keys[pos] == key) {
                    return pos
                }
            }
            pos = (pos + 1) and mask
        }
    }

    private fun rehash(newCapacity: Int) {
        val oldKeys = keys
        val oldValues = values
        val oldState = state
        keys = LongArray(newCapacity)
        values = arrayOfNulls(newCapacity)
        state = ByteArray(newCapacity)
        count = 0
        deleted = 0
        val mask = newCapacity - 1
        for (i in oldState.indices) {
            if (oldState[i] == USED) {
                val key = oldKeys[i]
                var pos = hash(key) and mask
                while (state[pos] == USED) {
                    pos = (pos + 1) and mask
                }
                keys[pos] = key
                values[pos] = oldValues[i]
                state[pos] = USED.toByte()
                count++
            }
        }
    }

    override fun toString(): String {
        val builder = StringBuilder("{")
        var first = true
        forEachKeyValue { key, value ->
            if (!first) {
                builder.append(", ")
            }
            first = false
            builder.append(key).append('=').append(value)
        }
        return builder.append('}').toString()
    }

    private companion object {
        private const val EMPTY: Byte = 0
        private const val USED: Byte = 1
        private const val DELETED: Byte = 2

        private fun hash(value: Long): Int {
            var h = value * -7046029254386353131L
            h = h xor (h ushr 32)
            return h.toInt()
        }
    }
}
