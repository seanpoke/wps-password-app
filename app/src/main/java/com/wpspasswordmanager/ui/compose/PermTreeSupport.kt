package com.wpspasswordmanager.ui.compose

/**
 * 文档权限树的共享纯逻辑（App 内 PermTreeSheet 与悬浮窗 AssistantPermTreePanel 共用）。
 * 从 AppViewModel 抽离，避免两套解析/遍历实现漂移。
 *
 * 节点唯一键统一为 AuthNode.key（type:id）：接口文档明确 dn 可能为 null 且会省略该字段，
 * 因此 selected / expanded / 节点查找 / LazyColumn 的 key 一律不得使用 dn。
 */

internal fun parseAuthNode(
    o: org.json.JSONObject,
    parentAuth: Boolean,
    selected: MutableSet<String>,
    expanded: MutableSet<String>,
    depth: Int
): AuthNode {
    val type = o.optInt("type")
    val id = o.optLong("id")
    val has = o.optBoolean("hasAuth", false)
    val key = "$type:$id"
    val children = mutableListOf<AuthNode>()
    o.optJSONArray("deptList")?.let { a ->
        for (i in 0 until a.length()) children += parseAuthNode(a.getJSONObject(i), has, selected, expanded, depth + 1)
    }
    o.optJSONArray("employList")?.let { a ->
        for (i in 0 until a.length()) children += parseAuthNode(a.getJSONObject(i), has, selected, expanded, depth + 1)
    }
    if (has && !parentAuth) selected += key
    if (type == 0 && depth <= 1) expanded += key
    return AuthNode(
        dn = o.optString("dn").takeIf { it.isNotEmpty() && it != "null" },
        type = type,
        name = o.optString("name"),
        account = o.optString("account").takeIf { it.isNotEmpty() && it != "null" },
        hasAuth = has,
        children = children,
        id = id
    )
}

internal fun findAuthNode(nodes: List<AuthNode>, key: String): AuthNode? {
    nodes.forEach { n ->
        if (n.key == key) return n
        findAuthNode(n.children, key)?.let { return it }
    }
    return null
}

internal fun collectAuthKeys(node: AuthNode): List<String> =
    listOf(node.key) + node.children.flatMap { collectAuthKeys(it) }

internal fun collectDeptKeys(nodes: List<AuthNode>): List<String> =
    nodes.filter { it.isDept }.flatMap { listOf(it.key) + collectDeptKeys(it.children) }
