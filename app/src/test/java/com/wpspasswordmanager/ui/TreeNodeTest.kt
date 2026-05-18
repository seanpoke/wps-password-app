package com.wpspasswordmanager.ui

import org.junit.Test
import org.junit.Assert.*

class TreeNodeTest {

    @Test
    fun testVirtualHasAuth_DirectSelected() {
        val node = TreeNode("dn1", "部门1", null, 0, hasAuth = true)
        assertTrue("直接选中的节点虚拟状态应为true", node.getVirtualHasAuth())
    }

    @Test
    fun testVirtualHasAuth_NotSelected() {
        val node = TreeNode("dn1", "部门1", null, 0, hasAuth = false)
        assertFalse("未选中的节点虚拟状态应为false", node.getVirtualHasAuth())
    }

    @Test
    fun testVirtualHasAuth_InheritFromParent() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = true)
        val child = TreeNode("dn2", "子部门", null, 0, hasAuth = false)
        child.parent = parent
        parent.children.add(child)
        
        assertTrue("子节点应继承父节点的选中状态", child.getVirtualHasAuth())
    }

    @Test
    fun testVirtualHasAuth_MultilevelInheritance() {
        val level1 = TreeNode("dn1", "一级部门", null, 0, hasAuth = true)
        val level2 = TreeNode("dn2", "二级部门", null, 0, hasAuth = false)
        val level3 = TreeNode("dn3", "三级部门", null, 0, hasAuth = false)
        
        level2.parent = level1
        level1.children.add(level2)
        level3.parent = level2
        level2.children.add(level3)
        
        assertTrue("三级节点应继承一级父节点的选中状态", level3.getVirtualHasAuth())
    }

    @Test
    fun testVirtualHasAuth_NoInheritanceWhenParentNotSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child = TreeNode("dn2", "子部门", null, 0, hasAuth = false)
        child.parent = parent
        parent.children.add(child)
        
        assertFalse("父节点未选中时子节点不应被继承选中", child.getVirtualHasAuth())
    }

    @Test
    fun testUpdateChildrenAuthState_SelectParent() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = false)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        parent.setAuthState(true)
        parent.updateChildrenAuthState(true)
        
        assertTrue("子部门1应被选中", child1.hasAuth)
        assertTrue("子部门2应被选中", child2.hasAuth)
    }

    @Test
    fun testUpdateChildrenAuthState_DeselectParent() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = true)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = true)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        parent.setAuthState(false)
        parent.updateChildrenAuthState(false)
        
        assertFalse("子部门1应取消选中", child1.hasAuth)
        assertFalse("子部门2应取消选中", child2.hasAuth)
    }

    @Test
    fun testAreAllChildrenSelected_AllSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = true)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertTrue("所有子节点都选中时应返回true", parent.areAllChildrenSelected())
    }

    @Test
    fun testAreAllChildrenSelected_PartialSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertFalse("部分子节点选中时应返回false", parent.areAllChildrenSelected())
    }

    @Test
    fun testAreAllChildrenSelected_NoneSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = false)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertFalse("没有子节点选中时应返回false", parent.areAllChildrenSelected())
    }

    @Test
    fun testHasSelectedChildren_HasSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertTrue("有子节点选中时应返回true", parent.hasSelectedChildren())
    }

    @Test
    fun testHasSelectedChildren_NoSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = false)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertFalse("没有子节点选中时应返回false", parent.hasSelectedChildren())
    }

    @Test
    fun testHasSelectedChildren_EmptyChildren() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        
        assertFalse("没有子节点时应返回false", parent.hasSelectedChildren())
    }

    @Test
    fun testShouldShowIndeterminate_PartialSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertTrue("部分子节点选中时应显示半选状态", parent.shouldShowIndeterminate())
    }

    @Test
    fun testShouldShowIndeterminate_AllSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = true)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertFalse("所有子节点都选中时不应显示半选状态", parent.shouldShowIndeterminate())
    }

    @Test
    fun testShouldShowIndeterminate_NoneSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = false)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        assertFalse("没有子节点选中时不应显示半选状态", parent.shouldShowIndeterminate())
    }

    @Test
    fun testShouldShowIndeterminate_EmptyChildren() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        
        assertFalse("没有子节点时不应显示半选状态", parent.shouldShowIndeterminate())
    }

    @Test
    fun testGetAllSelectedNodes_SingleNode() {
        val node = TreeNode("dn1", "部门1", null, 0, hasAuth = true)
        val result = node.getAllSelectedNodes()
        
        assertEquals(1, result.size)
        assertEquals("部门1", result[0].name)
    }

    @Test
    fun testGetAllSelectedNodes_ParentAndChildren() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = true)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        val result = parent.getAllSelectedNodes()
        
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "父部门" })
        assertTrue(result.any { it.name == "子部门1" })
        assertFalse(result.any { it.name == "子部门2" })
    }

    @Test
    fun testGetAllSelectedDeptDns_OnlyParentSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = true)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = false)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        val result = parent.getAllSelectedDeptDns()
        
        assertEquals(1, result.size)
        assertEquals("dn1", result[0])
    }

    @Test
    fun testGetAllSelectedDeptDns_ParentAndChildrenSelected() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = true)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        val result = parent.getAllSelectedDeptDns()
        
        assertEquals(2, result.size)
        assertTrue(result.contains("dn1"))
        assertTrue(result.contains("dn2"))
    }

    @Test
    fun testEmptyDepartmentList() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        
        assertTrue("空部门列表应返回true", parent.areAllChildrenSelected())
        assertFalse("空部门列表应返回false", parent.hasSelectedChildren())
        assertFalse("空部门列表不应显示半选", parent.shouldShowIndeterminate())
    }

    @Test
    fun testSingleLevelDepartment() {
        val parent = TreeNode("dn1", "根部门", null, 0, hasAuth = false)
        val child = TreeNode("dn2", "子部门", null, 0, hasAuth = true)
        
        child.parent = parent
        parent.children.add(child)
        
        assertFalse(parent.areAllChildrenSelected())
        assertTrue(parent.hasSelectedChildren())
        assertTrue(parent.shouldShowIndeterminate())
    }

    @Test
    fun testMultiLevelDepartment_Inheritance() {
        val level1 = TreeNode("dn1", "一级部门", null, 0, hasAuth = true)
        val level2 = TreeNode("dn2", "二级部门", null, 0, hasAuth = false)
        val level3 = TreeNode("dn3", "三级部门", null, 0, hasAuth = false)
        val employee = TreeNode("dn4", "员工", "emp001", 1, hasAuth = false)
        
        level2.parent = level1
        level1.children.add(level2)
        level3.parent = level2
        level2.children.add(level3)
        employee.parent = level3
        level3.children.add(employee)
        
        assertTrue("二级部门应继承一级部门权限", level2.getVirtualHasAuth())
        assertTrue("三级部门应继承一级部门权限", level3.getVirtualHasAuth())
        assertTrue("员工应继承一级部门权限", employee.getVirtualHasAuth())
        
        assertFalse("二级部门实际hasAuth应为false", level2.hasAuth)
        assertFalse("三级部门实际hasAuth应为false", level3.hasAuth)
        assertFalse("员工实际hasAuth应为false", employee.hasAuth)
    }

    @Test
    fun testToggleExpansion() {
        val node = TreeNode("dn1", "部门", null, 0, hasAuth = false)
        
        assertFalse("初始状态应为折叠", node.isExpanded)
        node.toggleExpansion()
        assertTrue("切换后应为展开", node.isExpanded)
        node.toggleExpansion()
        assertFalse("再次切换后应为折叠", node.isExpanded)
    }

    @Test
    fun testUpdateChildrenAuthState_PreserveManualSelection() {
        val parent = TreeNode("dn1", "父部门", null, 0, hasAuth = false)
        val child1 = TreeNode("dn2", "子部门1", null, 0, hasAuth = true)
        val child2 = TreeNode("dn3", "子部门2", null, 0, hasAuth = false)
        
        child1.parent = parent
        child2.parent = parent
        parent.children.addAll(listOf(child1, child2))
        
        parent.setAuthState(true)
        parent.updateChildrenAuthState(false, updateAll = false)
        
        assertTrue("已手动选中的子节点应保持选中状态", child1.hasAuth)
        assertFalse("未手动选中的子节点应保持未选中状态", child2.hasAuth)
    }
}