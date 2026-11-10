package org.mapdb

import org.mapdb.IndexTreeListJava.*

/**
 * Primitive sorted Map<Long, Long>.
 *
 * Android-safe: implements the local [MutableLongLongMap] contract instead of
 * Eclipse Collections interfaces (which extend java.util.function, API 24+).
 */
class IndexTreeLongLongMap(
    val store: Store,
    val rootRecid: Long,
    val dirShift: Int,
    val levels: Int,
    val collapseOnRemove: Boolean
) : MutableLongLongMap {

    companion object {

        /** constructor with default values */
        fun make(
            store: Store = StoreTrivial(),
            rootRecid: Long = store.put(dirEmpty(), dirSer),
            dirShift: Int = CC.INDEX_TREE_LONGLONGMAP_DIR_SHIFT,
            levels: Int = CC.INDEX_TREE_LONGLONGMAP_LEVELS,
            collapseOnRemove: Boolean = true
        ) = IndexTreeLongLongMap(
            store = store,
            rootRecid = rootRecid,
            dirShift = dirShift,
            levels = levels,
            collapseOnRemove = collapseOnRemove
        )
    }

    override fun put(key: Long, value: Long) {
        assertKey(key)
        treePut(dirShift, rootRecid, store, levels, key, value)
    }

    override fun get(key: Long): Long {
        assertKey(key)
        return treeGet(dirShift, rootRecid, store, levels, key)
    }

    override fun remove(key: Long) {
        assertKey(key)
        if (collapseOnRemove) {
            treeRemoveCollapsing(dirShift, rootRecid, store, levels, true, key, null)
        } else {
            treeRemove(dirShift, rootRecid, store, levels, key, null)
        }
    }

    override fun containsKey(key: Long): Boolean {
        if (key < 0L) {
            return false
        }
        return treeGetNullable(dirShift, rootRecid, store, levels, key) != null
    }

    override fun clear() {
        treeClear(rootRecid, store, levels)
    }

    override val size: Int
        get() = Utils.roundDownToIntMAXVAL(
            treeFold(rootRecid, store, levels, 0L) { _, _, count: Long ->
                count + 1
            }
        )

    override val isEmpty: Boolean
        get() = treeIter(dirShift, rootRecid, store, levels, 0) == null

    override fun forEachKey(action: (Long) -> Unit) {
        treeFold(rootRecid, store, levels, Unit) { key, _, _ ->
            action(key)
        }
    }

    override fun forEachValue(action: (Long) -> Unit) {
        treeFold(rootRecid, store, levels, Unit) { _, value, _ ->
            action(value)
        }
    }

    override fun forEachKeyValue(action: (Long, Long) -> Unit) {
        treeFold(rootRecid, store, levels, Unit) { key, value, _ ->
            action(key, value)
        }
    }

    override fun longIterator(): LongIterator = MapIterator(this, 1)

    override fun keys(): LongIterable = object : LongIterable {
        override fun longIterator(): LongIterator = MapIterator(this@IndexTreeLongLongMap, 0)
    }

    override fun values(): LongIterable = object : LongIterable {
        override fun longIterator(): LongIterator = MapIterator(this@IndexTreeLongLongMap, 1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MutableLongLongMap) {
            return false
        }
        var count = 0
        var result = true
        forEachKeyValue { key, value ->
            count++
            if (!other.containsKey(key) || other.get(key) != value) {
                result = false
            }
        }
        return result && count == other.size
    }

    override fun hashCode(): Int {
        var result = 0
        forEachKeyValue { key, value ->
            result += DataIO.longHash(key + value + 10)
        }
        return result
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

    private fun assertKey(key: Long) {
        if (key < 0) {
            throw IllegalArgumentException("negative key")
        }
        //TODO assert max size based on levels
    }

    private class MapIterator(
        private val map: IndexTreeLongLongMap,
        private val index: Int
    ) : LongIterator(), MutableIterator<Long> {

        private var nextKey: Long? = -1L
        private var nextRet: Long? = null
        private var lastKey: Long? = null

        override fun hasNext(): Boolean {
            if (nextRet != null) {
                return true
            }
            val prev = nextKey ?: return false
            val ret = treeIter(map.dirShift, map.rootRecid, map.store, map.levels, prev + 1)
            if (ret == null) {
                nextRet = null
                nextKey = null
            } else {
                nextRet = ret[index]
                nextKey = ret[0]
            }
            return nextRet != null
        }

        override fun nextLong(): Long {
            val ret = nextRet
            nextRet = null
            if (ret == null) {
                if (nextKey != null) {
                    //fetch next item
                    if (hasNext()) {
                        return nextLong()
                    }
                }
                lastKey = null
                throw NoSuchElementException()
            }
            lastKey = nextKey
            return ret
        }

        override fun remove() {
            map.remove(lastKey ?: throw IllegalStateException())
            lastKey = null
        }
    }
}
