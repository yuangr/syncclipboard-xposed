package io.github.erenche.syncclipboard.common.clipboard

import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import io.github.erenche.syncclipboard.common.util.Logger
import io.github.erenche.syncclipboard.common.util.SafeDiagnostics
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shizuku 剪贴板访问器。
 *
 * 适用于普通应用进程在无 Root 权限下通过 Shizuku 代理系统剪贴板服务（IClipboard）。
 *
 * 核心机制：
 * 1. 权限与凭据：通过 [ShizukuBinderWrapper] 将 Binder 事务投递至 Shizuku 服务端（UID 2000 shell），
 *    调用系统服务时向系统呈递 "com.android.shell" 凭据。
 * 2. 多版本签名自适应：Android 10 至 15+ 期间，IClipboard 的 setPrimaryClip / getPrimaryClip /
 *    addPrimaryClipChangedListener 签名多次变更（追加 attributionTag, userId, deviceId）。
 *    本实现通过动态反射匹配参数签名，杜绝 NoSuchMethodException 导致进程崩溃。
 * 3. 动态 Binder 代理：动态实现 android.content.IOnPrimaryClipChangedListener 接口，
 *    通过 Binder onTransact(1) 捕获跨进程回调并通知上层引擎。
 * 4. 生命周期鲁棒性：注册 BinderReceived 与 BinderDead 监听，Shizuku 服务重启或断连时自动复位与重建。
 */
class ShizukuClipboardAccessor(
    private val context: Context
) : ClipboardAccessor {

    override val name: String = "ShizukuClipboard"

    private val listeners = CopyOnWriteArrayList<ClipboardAccessor.OnPrimaryClipChangedListener>()

    @Volatile
    private var iClipboardInstance: Any? = null

    @Volatile
    private var cachedSetClipMethod: Method? = null

    @Volatile
    private var cachedGetClipMethod: Method? = null

    @Volatile
    private var cachedAddListenerMethod: Method? = null

    @Volatile
    private var cachedRemoveListenerMethod: Method? = null

    @Volatile
    private var activeProxyListener: Any? = null

    @Volatile
    private var isListenerRegisteredOnSystem = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Logger.info(name, "Shizuku binder received, evaluating clipboard availability")
        invalidateCache()
        if (listeners.isNotEmpty()) {
            ensureSystemListenerRegistered()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Logger.warn(name, "Shizuku binder dead, resetting clipboard bridge")
        invalidateCache()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
            Logger.warn(name, "Failed to register Shizuku lifecycle listeners: ${SafeDiagnostics.failure(e)}")
        }
    }

    @Synchronized
    private fun invalidateCache() {
        iClipboardInstance = null
        cachedSetClipMethod = null
        cachedGetClipMethod = null
        cachedAddListenerMethod = null
        cachedRemoveListenerMethod = null
        activeProxyListener = null
        isListenerRegisteredOnSystem = false
    }

    override fun isAvailable(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    private fun getClipboardService(): Any? {
        if (iClipboardInstance != null) return iClipboardInstance
        if (!isAvailable()) {
            Logger.warn(name, "getClipboardService skipped: Shizuku unavailable or permission not granted")
            return null
        }

        return try {
            val rawBinder: IBinder? = SystemServiceHelper.getSystemService("clipboard")
            if (rawBinder == null) {
                Logger.warn(name, "SystemServiceHelper.getSystemService(\"clipboard\") returned null")
                return null
            }
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.content.IClipboard\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterface.invoke(null, wrappedBinder)
            iClipboardInstance = service
            Logger.info(name, "Successfully connected to IClipboard via Shizuku")
            service
        } catch (e: Throwable) {
            Logger.error(name, "Failed to obtain IClipboard via Shizuku: ${SafeDiagnostics.failure(e)}")
            null
        }
    }

    override fun getPrimaryClip(): ClipData? {
        val service = getClipboardService() ?: return null
        return try {
            var method = cachedGetClipMethod
            if (method == null) {
                val methods = service.javaClass.methods.filter { it.name == "getPrimaryClip" }
                for (m in methods) {
                    val p = m.parameterTypes
                    val args = buildArgsForGet(p)
                    try {
                        val res = m.invoke(service, *args)
                        cachedGetClipMethod = m
                        return res as? ClipData
                    } catch (_: Exception) {
                    }
                }
            } else {
                val args = buildArgsForGet(method.parameterTypes)
                return method.invoke(service, *args) as? ClipData
            }
            null
        } catch (e: Throwable) {
            Logger.warn(name, "getPrimaryClip failed via Shizuku: ${SafeDiagnostics.failure(e)}")
            null
        }
    }

    override fun setPrimaryClip(clip: ClipData): Boolean {
        val service = getClipboardService() ?: return false
        return try {
            var method = cachedSetClipMethod
            if (method == null) {
                val methods = service.javaClass.methods.filter { it.name == "setPrimaryClip" }
                for (m in methods) {
                    val p = m.parameterTypes
                    if (p.isEmpty() || p[0] != ClipData::class.java) continue
                    val args = buildArgsForSet(p, clip)
                    try {
                        m.invoke(service, *args)
                        cachedSetClipMethod = m
                        Logger.info(name, "setPrimaryClip success via Shizuku (params: ${p.size})")
                        return true
                    } catch (e: Exception) {
                        Logger.warn(name, "Method variant ${m.toGenericString()} failed: ${e.message}")
                    }
                }
            } else {
                val args = buildArgsForSet(method.parameterTypes, clip)
                method.invoke(service, *args)
                return true
            }
            false
        } catch (e: Throwable) {
            Logger.error(name, "setPrimaryClip failed via Shizuku: ${SafeDiagnostics.failure(e)}")
            false
        }
    }

    override fun addPrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
        listeners.add(listener)
        ensureSystemListenerRegistered()
    }

    override fun removePrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            unregisterSystemListener()
        }
    }

    @Synchronized
    private fun ensureSystemListenerRegistered() {
        if (isListenerRegisteredOnSystem) return
        val service = getClipboardService() ?: return

        try {
            val listenerInterface = Class.forName("android.content.IOnPrimaryClipChangedListener")

            val binder = object : Binder(), IInterface {
                init {
                    attachInterface(this, "android.content.IOnPrimaryClipChangedListener")
                }
                override fun asBinder(): IBinder = this

                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code == INTERFACE_TRANSACTION) {
                        reply?.writeString("android.content.IOnPrimaryClipChangedListener")
                        return true
                    }
                    if (code == FIRST_CALL_TRANSACTION /* 1: dispatchPrimaryClipChanged */) {
                        data.enforceInterface("android.content.IOnPrimaryClipChangedListener")
                        dispatchClipChanged()
                        return true
                    }
                    return super.onTransact(code, data, reply, flags)
                }
            }

            val proxy = Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, _ ->
                when (method.name) {
                    "asBinder" -> binder
                    "dispatchPrimaryClipChanged" -> {
                        dispatchClipChanged()
                        null
                    }
                    "equals" -> false
                    "hashCode" -> binder.hashCode()
                    "toString" -> "IOnPrimaryClipChangedListenerProxy"
                    else -> null
                }
            }

            var method = cachedAddListenerMethod
            if (method == null) {
                val methods = service.javaClass.methods.filter { it.name == "addPrimaryClipChangedListener" }
                for (m in methods) {
                    val p = m.parameterTypes
                    if (p.isEmpty() || !p[0].isAssignableFrom(listenerInterface)) continue
                    val args = buildArgsForListener(p, proxy)
                    try {
                        m.invoke(service, *args)
                        cachedAddListenerMethod = m
                        activeProxyListener = proxy
                        isListenerRegisteredOnSystem = true
                        Logger.info(name, "Shizuku IOnPrimaryClipChangedListener registered successfully (params: ${p.size})")
                        return
                    } catch (e: Exception) {
                        Logger.warn(name, "addPrimaryClipChangedListener variant failed: ${e.message}")
                    }
                }
            } else {
                val args = buildArgsForListener(method.parameterTypes, proxy)
                method.invoke(service, *args)
                activeProxyListener = proxy
                isListenerRegisteredOnSystem = true
                Logger.info(name, "Shizuku IOnPrimaryClipChangedListener registered with cached method")
            }
        } catch (e: Throwable) {
            Logger.error(name, "Failed to register Shizuku clip listener: ${SafeDiagnostics.failure(e)}")
        }
    }

    @Synchronized
    private fun unregisterSystemListener() {
        if (!isListenerRegisteredOnSystem) return
        val proxy = activeProxyListener ?: return
        val service = getClipboardService() ?: return

        try {
            var method = cachedRemoveListenerMethod
            if (method == null) {
                val listenerInterface = Class.forName("android.content.IOnPrimaryClipChangedListener")
                val methods = service.javaClass.methods.filter { it.name == "removePrimaryClipChangedListener" }
                for (m in methods) {
                    val p = m.parameterTypes
                    if (p.isEmpty() || !p[0].isAssignableFrom(listenerInterface)) continue
                    val args = buildArgsForListener(p, proxy)
                    try {
                        m.invoke(service, *args)
                        cachedRemoveListenerMethod = m
                        break
                    } catch (_: Exception) {
                    }
                }
            } else {
                val args = buildArgsForListener(method.parameterTypes, proxy)
                method.invoke(service, *args)
            }
            Logger.info(name, "Shizuku IOnPrimaryClipChangedListener unregistered successfully")
        } catch (e: Throwable) {
            Logger.warn(name, "unregisterSystemListener error: ${SafeDiagnostics.failure(e)}")
        } finally {
            activeProxyListener = null
            isListenerRegisteredOnSystem = false
        }
    }

    private fun dispatchClipChanged() {
        for (l in listeners) {
            try {
                l.onPrimaryClipChanged()
            } catch (e: Throwable) {
                Logger.warn(name, "dispatchClipChanged callback error: ${e.message}")
            }
        }
    }

    private fun buildArgsForSet(paramTypes: Array<Class<*>>, clip: ClipData): Array<Any?> {
        val args = arrayOfNulls<Any>(paramTypes.size)
        args[0] = clip
        for (i in 1 until paramTypes.size) {
            args[i] = when (paramTypes[i]) {
                String::class.java -> if (i == 1) "com.android.shell" else null
                Int::class.javaPrimitiveType -> 0 // userId=0, deviceId=0
                else -> null
            }
        }
        return args
    }

    private fun buildArgsForGet(paramTypes: Array<Class<*>>): Array<Any?> {
        val args = arrayOfNulls<Any>(paramTypes.size)
        for (i in paramTypes.indices) {
            args[i] = when (paramTypes[i]) {
                String::class.java -> if (i == 0) "com.android.shell" else null
                Int::class.javaPrimitiveType -> 0 // userId=0, deviceId=0
                else -> null
            }
        }
        return args
    }

    private fun buildArgsForListener(paramTypes: Array<Class<*>>, proxy: Any): Array<Any?> {
        val args = arrayOfNulls<Any>(paramTypes.size)
        args[0] = proxy
        for (i in 1 until paramTypes.size) {
            args[i] = when (paramTypes[i]) {
                String::class.java -> if (i == 1) "com.android.shell" else null
                Int::class.javaPrimitiveType -> 0 // userId=0, deviceId=0
                else -> null
            }
        }
        return args
    }
}
