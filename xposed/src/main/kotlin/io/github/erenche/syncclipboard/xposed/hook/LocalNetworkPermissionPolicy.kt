package io.github.erenche.syncclipboard.xposed.hook

/** 权限掩码处理；流量表与分块表使用不同的位定义。 */
internal object LocalNetworkPermissionPolicy {
    private const val PER_USER_RANGE = 100_000

    fun permissionId(uid: Int, appIdKeyed: Boolean): Int =
        if (appIdKeyed) uid % PER_USER_RANGE else uid

    fun supplement(original: Int, localNetworkBit: Int, enabled: Boolean): Int =
        // 负值表示删除操作，不追加权限位。
        if (enabled && original >= 0 && localNetworkBit > 0) original or localNetworkBit else original

    fun otherIds(ids: IntArray, targetId: Int): IntArray = ids.filter { it != targetId }.toIntArray()
}
