package org.mapdb

/**
 * Primitive long hash set (open addressing, no java.util.function).
 *
 * Android-safe replacement for org.eclipse.collections.impl.set.mutable.primitive.LongHashSet.
 */
class LongHashSet(initialCapacity: Int = 16) : LongSet {

    private var keys: LongArray
    private var state: ByteArray
    private var count: Int = 0
    private var deleted: Int = 0

    init {
        var capacity = 16
        while (capacity < initialCapacity * 2) {
            capacity = capacity shl 1
        }
        keys = LongArray(capacity)
        state = ByteArray(capacity)
    }

    override val size: Int
        get() = count

    override val isEmpty: Boolean
        get() = count == 0

    fun add(value: Long): Boolean {
        if ((count + deleted + 1) * 3 >= keys.size * 2) {
            rehash(keys.size * 2)
        }
        val mask = keys.size - 1
        var pos = hash(value) and mask
        var firstDeleted = -1
        while (true) {
            when (state[pos]) {
                EMPTY -> {
                    if (firstDeleted >= 0) {
                        pos = firstDeleted
                        deleted--
                    }
                    keys[pos] = value
                    state[pos] = USED.toByte()
                    count++
                    return true
                }
                USED -> if (keys[pos] == value) {
                    return false
                }
                else -> if (firstDeleted < 0) {
                    firstDeleted = pos
                }
            }
            pos = (pos + 1) and mask
        }
    }

    fun addAll(values: LongIterable) {
        val array = values.toArray()
        for (value in array) {
            add(value)
        }
    }

    override fun contains(value: Long): Boolean = indexOf(value) >= 0

    fun remove(value: Long): Boolean {
        val pos = indexOf(value)
        if (pos < 0) {
            return false
        }
        state[pos] = DELETED.toByte()
        count--
        deleted++
        return true
    }

    fun clear() {
        keys = LongArray(16)
        state = ByteArray(16)
        count = 0
        deleted = 0
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LongSet) {
            return false
        }
        if (other.size != count) {
            return false
        }
        val iterator = longIterator()
        while (iterator.hasNext()) {
            if (!other.contains(iterator.next())) {
                return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = 0
        forEach { value ->
            result += (value xor (value ushr 32)).toInt()
        }
        return result
    }

    override fun toString(): String = makeString("[", ", ", "]")

    private fun indexOf(value: Long): Int {
        val mask = keys.size - 1
        var pos = hash(value) and mask
        while (true) {
            when (state[pos]) {
                EMPTY -> return -1
                USED -> if (keys[pos] == value) {
                    return pos
                }
            }
            pos = (pos + 1) and mask
        }
    }

    private fun rehash(newCapacity: Int) {
        val oldKeys = keys
        val oldState = state
        keys = LongArray(newCapacity)
        state = ByteArray(newCapacity)
        count = 0
        deleted = 0
        val mask = newCapacity - 1
        for (i in oldState.indices) {
            if (oldState[i] == USED) {
                val value = oldKeys[i]
                var pos = hash(value) and mask
                while (state[pos] == USED) {
                    pos = (pos + 1) and mask
                }
                keys[pos] = value
                state[pos] = USED.toByte()
                count++
            }
        }
    }

    companion object {
        private const val EMPTY: Byte = 0
        private const val USED: Byte = 1
        private const val DELETED: Byte = 2

        private fun hash(value: Long): Int {
            var h = value * -7046029254386353131L
            h = h xor (h ushr 32)
            return h.toInt()
        }

        @JvmStatic
        fun newSetWith(vararg values: Long): LongHashSet {
            val set = LongHashSet(values.size * 2)
            for (value in values) {
                set.add(value)
            }
            return set
        }

        @JvmStatic
        fun newSet(values: LongIterable): LongHashSet {
            val set = LongHashSet()
            set.addAll(values)
            return set
        }
    }
}
