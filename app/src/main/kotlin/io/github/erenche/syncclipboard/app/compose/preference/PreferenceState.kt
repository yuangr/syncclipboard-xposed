package io.github.erenche.syncclipboard.app.compose.preference

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** 记住布尔设置，写入时同步持久化。 */
@Composable
fun rememberBooleanPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val state = remember(prefs, key, defaultValue) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    return remember(prefs, key, state) {
        object : MutableState<Boolean> by state {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    prefs.edit().putBoolean(key, newValue).apply()
                    state.value = newValue
                }
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

/** 记住字符串设置，写入时同步持久化。 */
@Composable
fun rememberStringPreference(
    prefs: SharedPreferences,
    key: String,
    defaultValue: String
): MutableState<String> {
    val state = remember(prefs, key, defaultValue) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    return remember(prefs, key, state) {
        object : MutableState<String> by state {
            override var value: String
                get() = state.value
                set(newValue) {
                    prefs.edit().putString(key, newValue).apply()
                    state.value = newValue
                }
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}
