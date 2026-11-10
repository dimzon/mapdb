package org.mapdb

import java.util.Arrays

/**
 * Primitive long LIFO stack.
 *
 * Android-safe replacement for org.eclipse.collections.impl.stack.mutable.primitive.LongArrayStack.
 */
class LongArrayStack(initialCapacity: Int = 16) : LongIterable {

    private var elements = LongArray(if (initialCapacity <= 0) 1 else initialCapacity)
    private var count: Int = 0

    val size: Int
        get() = count

    override val isEmpty: Boolean
        get() = count == 0

    fun push(value: Long) {
        if (count == elements.size) {
            elements = Arrays.copyOf(elements, elements.size * 2)
        }
        elements[count++] = value
    }

    fun pop(): Long {
        if (count == 0) {
            throw NoSuchElementException()
        }
        return elements[--count]
    }

    fun peek(): Long {
        if (count == 0) {
            throw NoSuchElementException()
        }
        return elements[count - 1]
    }

    fun clear() {
        count = 0
    }

    override fun longIterator(): LongIterator = object : LongIterator() {
        private var pos = count

        override fun hasNext(): Boolean = pos > 0

        override fun nextLong(): Long {
            if (pos <= 0) {
                throw NoSuchElementException()
            }
            return elements[--pos]
        }
    }

    override fun toArray(): LongArray = elements.copyOf(count)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other !is LongIterable) {
            return false
        }
        val iterator = other.longIterator()
        var pos = count
        while (iterator.hasNext()) {
            if (pos <= 0 || elements[--pos] != iterator.next()) {
                return false
            }
        }
        return pos == 0
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until count) {
            val value = elements[i]
            result = 31 * result + (value xor (value ushr 32)).toInt()
        }
        return result
    }

    override fun toString(): String = makeString("[", ", ", "]")
}
