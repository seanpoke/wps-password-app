package com.wpspasswordmanager.business

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrderedSetTest {

    private lateinit var orderedSet: OrderedSet<String>

    @Before
    fun setUp() {
        orderedSet = OrderedSet()
    }

    @Test
    fun `test insertion order`() {
        // 按顺序插入 b, a, c
        orderedSet.add("b")
        orderedSet.add("a")
        orderedSet.add("c")
        
        // 遍历应该返回 [c, a, b]
        val result = mutableListOf<String>()
        orderedSet.iterator().forEach { result.add(it) }
        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun `test duplicate element handling`() {
        // 按顺序插入 b, a, c
        orderedSet.add("b")
        orderedSet.add("a")
        orderedSet.add("c")
        
        // 再次插入 a
        orderedSet.add("a")
        
        // 遍历应该返回 [a, c, b]
        val result = mutableListOf<String>()
        orderedSet.iterator().forEach { result.add(it) }
        assertEquals(listOf("a", "c", "b"), result)
        
        // 集合大小应该仍然是 3
        assertEquals(3, orderedSet.size)
    }

    @Test
    fun `test basic operations`() {
        // 测试添加元素
        orderedSet.add("test")
        assertEquals(1, orderedSet.size)
        assertTrue(orderedSet.contains("test"))
        
        // 测试移除元素
        orderedSet.remove("test")
        assertEquals(0, orderedSet.size)
        assertFalse(orderedSet.contains("test"))
        
        // 测试清空集合
        orderedSet.add("a")
        orderedSet.add("b")
        assertEquals(2, orderedSet.size)
        orderedSet.clear()
        assertEquals(0, orderedSet.size)
        assertTrue(orderedSet.isEmpty())
    }

    @Test
    fun `test boundary conditions`() {
        // 测试空集合
        assertTrue(orderedSet.isEmpty())
        assertEquals(0, orderedSet.size)
        assertNull(orderedSet.firstOrNull())
        
        // 测试单元素集合
        orderedSet.add("single")
        assertFalse(orderedSet.isEmpty())
        assertEquals(1, orderedSet.size)
        assertEquals("single", orderedSet.firstOrNull())
        
        // 测试多次插入同一元素
        orderedSet.add("single")
        orderedSet.add("single")
        assertEquals(1, orderedSet.size)
        assertEquals("single", orderedSet.firstOrNull())
    }

    @Test
    fun `test toList method`() {
        orderedSet.add("b")
        orderedSet.add("a")
        orderedSet.add("c")
        
        val list = orderedSet.toList()
        assertEquals(listOf("c", "a", "b"), list)
    }

    @Test
    fun `test firstOrNull method`() {
        // 空集合
        assertNull(orderedSet.firstOrNull())
        
        // 添加元素后
        orderedSet.add("first")
        assertEquals("first", orderedSet.firstOrNull())
        
        // 添加新元素后
        orderedSet.add("second")
        assertEquals("second", orderedSet.firstOrNull())
        
        // 再次添加第一个元素
        orderedSet.add("first")
        assertEquals("first", orderedSet.firstOrNull())
    }
}
