package com.wpspasswordmanager.ui.compose

/**
 * 文档权限树的共享纯逻辑（App 内 PermTreeSheet 与悬浮窗 AssistantPermTreePanel 共用）。
 * 从 AppViewModel 抽离，避免两套解析/遍历实现漂移。
 */

internal fun parseAuthNode(
    o: org.json.JSONObject,
    parentAuth: Boolean,
    selected: MutableSet<String>,
    expanded: MutableSet<String>,
    depth: Int
): AuthNode {
    val dn = o.optString("dn")
    val type = o.optInt("type")
    val has = o.optBoolean("hasAuth", false)
    val children = mutableListOf<AuthNode>()
    o.optJSONArray("deptList")?.let { a ->
        for (i in 0 until a.length()) children += parseAuthNode(a.getJSONObject(i), has, selected, expanded, depth + 1)
    }
    o.optJSONArray("employList")?.let { a ->
        for (i in 0 until a.length()) children += parseAuthNode(a.getJSONObject(i), has, selected, expanded, depth + 1)
    }
    if (has && !parentAuth) selected += dn
    if (type == 0 && depth <= 1) expanded += dn
    return AuthNode(
        dn = dn,
        type = type,
        name = o.optString("name"),
        account = o.optString("account").takeIf { it.isNotEmpty() && it != "null" },
        hasAuth = has,
        children = children,
        id = o.optLong("id")
    )
}

internal fun findAuthNode(nodes: List<AuthNode>, dn: String): AuthNode? {
    nodes.forEach { n ->
        if (n.dn == dn) return n
        findAuthNode(n.children, dn)?.let { return it }
    }
    return null
}

internal fun collectAuthDns(node: AuthNode): List<String> =
    listOf(node.dn) + node.children.flatMap { collectAuthDns(it) }

internal fun collectDeptDns(nodes: List<AuthNode>): List<String> =
    nodes.filter { it.isDept }.flatMap { listOf(it.dn) + collectDeptDns(it.children) }
