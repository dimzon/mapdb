package org.mapdb

/**
 * Primitive long container.
 *
 * Android-safe replacement for the Eclipse Collections types MapDB used before.
 * Nothing here touches java.util.function (API 24+), so it works on API 17.
 */
interface LongIterable {

    fun longIterator(): LongIterator

    val isEmpty: Boolean
        get() = !longIterator().hasNext()

    fun forEach(action: (Long) -> Unit) {
        val iterator = longIterator()
        while (iterator.hasNext()) {
            action(iterator.next())
        }
    }

    fun toArray(): LongArray {
        var result = LongArray(16)
        var pos = 0
        forEach { value ->
            if (pos == result.size) {
                result = result.copyOf(result.size * 2)
            }
            result[pos++] = value
        }
        return if (pos == result.size) result else result.copyOf(pos)
    }

    fun makeString(start: String, separator: String, end: String): String {
        val builder = StringBuilder(start)
        var first = true
        forEach { value ->
            if (!first) {
                builder.append(separator)
            }
            first = false
            builder.append(value)
        }
        return builder.append(end).toString()
    }
}

interface LongSet : LongIterable {

    fun contains(value: Long): Boolean

    val size: Int

    override val isEmpty: Boolean
        get() = size == 0
}

interface MutableLongList : LongIterable {

    val size: Int

    val first: Long
        get() = get(0)

    override val isEmpty: Boolean
        get() = size == 0

    operator fun get(index: Int): Long

    operator fun set(index: Int, value: Long)

    fun add(value: Long): Boolean

    fun addAll(values: LongIterable)

    fun clear()

    fun asSynchronized(): MutableLongList

    fun toReversed(): MutableLongList {
        val array = toArray()
        val result = LongArrayList(array.size)
        for (i in array.indices.reversed()) {
            result.add(array[i])
        }
        return result
    }
}

interface MutableLongLongMap : LongIterable {

    fun put(key: Long, value: Long)

    /** Returns 0 when the key is absent. */
    operator fun get(key: Long): Long

    fun remove(key: Long)

    fun containsKey(key: Long): Boolean

    fun clear()

    val size: Int

    override val isEmpty: Boolean
        get() = size == 0

    fun forEachKey(action: (Long) -> Unit)

    fun forEachValue(action: (Long) -> Unit)

    fun forEachKeyValue(action: (Long, Long) -> Unit)

    /** Values, iterated in the same order as [forEachValue]. */
    override fun longIterator(): LongIterator

    fun keys(): LongIterable

    fun values(): LongIterable
}
