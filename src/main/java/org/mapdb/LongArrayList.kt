package org.mapdb

import java.util.Arrays

/**
 * Growable primitive long list.
 *
 * Android-safe replacement for org.eclipse.collections.impl.list.mutable.primitive.LongArrayList.
 */
open class LongArrayList(initialCapacity: Int = 16) : MutableLongList {

    private var elements: LongArray
    private var count: Int = 0

    init {
        require(initialCapacity >= 0) { "negative capacity" }
        elements = LongArray(if (initialCapacity == 0) 1 else initialCapacity)
    }

    override val size: Int
        get() = count

    override val isEmpty: Boolean
        get() = count == 0

    override fun get(index: Int): Long {
        checkIndex(index)
        return elements[index]
    }

    override fun set(index: Int, value: Long) {
        checkIndex(index)
        elements[index] = value
    }

    override fun add(value: Long): Boolean {
        ensureCapacity(count + 1)
        elements[count++] = value
        return true
    }

    override fun addAll(values: LongIterable) {
        // toArray() also makes `list.addAll(list)` safe
        val array = values.toArray()
        ensureCapacity(count + array.size)
        System.arraycopy(array, 0, elements, count, array.size)
        count += array.size
    }

    override fun clear() {
        count = 0
    }

    override fun longIterator(): LongIterator = object : LongIterator() {
        private var pos = 0

        override fun hasNext(): Boolean = pos < count

        override fun nextLong(): Long {
            if (pos >= count) {
                throw NoSuchElementException()
            }
            return elements[pos++]
        }
    }

    override fun toArray(): LongArray = elements.copyOf(count)

    override fun asSynchronized(): MutableLongList = SynchronizedLongList(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is LongIterable) {
            return false
        }
        if (other is LongArrayList && other.count != count) {
            return false
        }
        val iterator = other.longIterator()
        var pos = 0
        while (iterator.hasNext()) {
            if (pos >= count || elements[pos] != iterator.next()) {
                return false
            }
            pos++
        }
        return pos == count
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

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("index: $index, size: $count")
        }
    }

    private fun ensureCapacity(required: Int) {
        if (required <= elements.size) {
            return
        }
        var newCapacity = elements.size
        while (newCapacity < required) {
            newCapacity = newCapacity shl 1
        }
        elements = Arrays.copyOf(elements, newCapacity)
    }

    companion object {
        @JvmStatic
        fun newListWith(vararg values: Long): LongArrayList {
            val list = LongArrayList(values.size)
            for (value in values) {
                list.add(value)
            }
            return list
        }
    }
}

private class SynchronizedLongList(
    private val delegate: MutableLongList
) : MutableLongList {

    override val size: Int
        get() = synchronized(delegate) { delegate.size }

    override val isEmpty: Boolean
        get() = synchronized(delegate) { delegate.isEmpty }

    override fun get(index: Int): Long = synchronized(delegate) { delegate.get(index) }

    override fun set(index: Int, value: Long) {
        synchronized(delegate) { delegate.set(index, value) }
    }

    override fun add(value: Long): Boolean = synchronized(delegate) { delegate.add(value) }

    override fun addAll(values: LongIterable) {
        // snapshot first, the source may be this very list
        val array = synchronized(delegate) { values.toArray() }
        synchronized(delegate) {
            for (value in array) {
                delegate.add(value)
            }
        }
    }

    override fun clear() {
        synchronized(delegate) { delegate.clear() }
    }

    override fun longIterator(): LongIterator =
        synchronized(delegate) { delegate.toArray() }.iterator()

    override fun toArray(): LongArray = synchronized(delegate) { delegate.toArray() }

    override fun asSynchronized(): MutableLongList = this

    override fun equals(other: Any?): Boolean = synchronized(delegate) { delegate == other }

    override fun hashCode(): Int = synchronized(delegate) { delegate.hashCode() }

    override fun toString(): String = synchronized(delegate) { delegate.toString() }
}
