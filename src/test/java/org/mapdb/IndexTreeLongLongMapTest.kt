package org.mapdb

import org.junit.Assert.*
import org.junit.Test
import java.util.*

class IndexTreeLongLongMapTest{

    @Test fun defaultSize(){

        val maxSize = Math.pow(1L.shl(CC.INDEX_TREE_LONGLONGMAP_DIR_SHIFT).toDouble(),CC.INDEX_TREE_LONGLONGMAP_LEVELS.toDouble())
        val expected = 100L * 1024*1024
        assertTrue(maxSize> expected)
        val map = IndexTreeLongLongMap.make()
        map.put(expected, 100L)
        assertEquals(1, map.size)
        assertEquals(100L, map.get(expected))
    }

    @Test fun get_Set(){
        val map = IndexTreeLongLongMap.make()

        map.put(0L, 111L)
        map.put(3423L, 4234L)

        assertEquals(111L, map.get(0L))
        assertEquals(4234L, map.get(3423L))
    }

    @Test fun key_iter(){
        val map = IndexTreeLongLongMap.make()

        map.put(0L, 111L)
        map.put(3423L, 4234L)

        val iter = map.keys().longIterator()
        assertTrue(iter.hasNext())
        assertEquals(0L, iter.next())
        assertTrue(iter.hasNext())
        assertEquals(3423L, iter.next())
        assertFalse(iter.hasNext())
        TT.assertFailsWith(NoSuchElementException::class.java, {
            iter.next()
        })
    }

    @Test fun value_iter(){
        val map = IndexTreeLongLongMap.make()

        map.put(0L, 111L)
        map.put(3423L, 4234L)

        val iter = map.values().longIterator()
        assertTrue(iter.hasNext())
        assertEquals(111L, iter.next())
        assertTrue(iter.hasNext())
        assertEquals(4234L, iter.next())
        assertFalse(iter.hasNext())
    }

    @Test fun zero_val(){
        val map = IndexTreeLongLongMap.make()
        map.put(0L,0L);
        assertTrue(map.containsKey(0L))
        map.put(33L,0L);
        assertTrue(map.containsKey(33L))
    }

    @Test fun remove_key(){
        val map = IndexTreeLongLongMap.make()
        map.put(1L, 2L)
        map.put(3L, 4L)
        assertEquals(2, map.size)
        map.remove(1L)
        assertEquals(0L, map.get(1L))
        assertFalse(map.containsKey(1L))
        assertEquals(1, map.size)
        map.clear()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty)
    }

    @Test fun forEachKeyVal(){
        val map = IndexTreeLongLongMap.make()
        val ref = LongLongHashMap()
        for(i in 0L until 1000){
            map.put(i, i*10)
        }
        assertEquals(1000, map.size)

        map.forEachKeyValue { key, value ->
            ref.put(key,value)
        }

        for(i in 0L until 1000){
            assertEquals(i*10, ref.get(i))
        }
    }


    @Test fun concurrent_modification() {
        if(TT.shortTest())
            return

        concModTest( IndexTreeLongLongMap.make(collapseOnRemove = true))
    }


    @Test fun concurrent_modification2() {
        if(TT.shortTest())
            return

        concModTest( IndexTreeLongLongMap.make(collapseOnRemove = false))
    }


    private fun concModTest(s1: IndexTreeLongLongMap) {

        val s2 = LongLongHashMap()
        val size = 1e7.toLong()

        for (i in 0L until size) {
            s1.put(i, i * 11)
            s2.put(i, i * 11)
        }
        val r = Random(1)
        val iter = s1.keys().longIterator()
        while (iter.hasNext()) {
            val next = iter.next()
            assertTrue("aa $next", s2.containsKey(next))
            assertEquals(next*11, s1.get(next) )

            val v = r.nextInt(size.toInt()).toLong()
            if (!s2.containsKey(v))
                continue

            s1.remove(v)
            s2.remove(v)
        }
    }
}
