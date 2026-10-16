package io.github.erenche.syncclipboard.common.model

import io.github.erenche.syncclipboard.common.util.HashUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 名称和密码变化不改变数据归属，地址、账号和存储位置变化则隔离数据。 */
fun ServerConfig.storageKey(): String = HashUtils.sha256(
    Json.encodeToString(copy(name = null, password = null, url = url.trim().trimEnd('/')))
)

fun AppConfig.activeServer(): ServerConfig? = servers.getOrNull(activeServerIndex)

fun AppConfig.serverStorageKey(): String = activeServer()?.storageKey() ?: "local"
