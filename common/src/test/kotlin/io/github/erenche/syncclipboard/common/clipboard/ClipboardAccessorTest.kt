package io.github.erenche.syncclipboard.common.clipboard

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ClipboardAccessorTest {

    private class MockClipboardAccessor : ClipboardAccessor {
        override val name: String = "MockClipboard"
        var available: Boolean = true
        var mockClipText: String? = null
        val listeners = mutableListOf<ClipboardAccessor.OnPrimaryClipChangedListener>()

        override fun isAvailable(): Boolean = available

        override fun getPrimaryClip(): android.content.ClipData? = null

        override fun setPrimaryClip(clip: android.content.ClipData): Boolean = true

        override fun addPrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
            listeners.add(listener)
        }

        override fun removePrimaryClipChangedListener(listener: ClipboardAccessor.OnPrimaryClipChangedListener) {
            listeners.remove(listener)
        }

        fun dispatchChange() {
            listeners.forEach { it.onPrimaryClipChanged() }
        }
    }

    @Test
    fun testListenerRegistrationAndDispatch() {
        val accessor = MockClipboardAccessor()
        val callbackCount = AtomicInteger(0)
        val listener = ClipboardAccessor.OnPrimaryClipChangedListener {
            callbackCount.incrementAndGet()
        }

        accessor.addPrimaryClipChangedListener(listener)
        accessor.dispatchChange()
        assertEquals(1, callbackCount.get())

        accessor.dispatchChange()
        assertEquals(2, callbackCount.get())

        // 负不变量验证：移除监听器后绝对不得再收到回调
        accessor.removePrimaryClipChangedListener(listener)
        accessor.dispatchChange()
        assertEquals(2, callbackCount.get())
    }

    /** 模拟 Android 10-11 签名的 IClipboard */
    interface MockIClipboardApi10 {
        fun setPrimaryClip(clip: Any, callingPackage: String, userId: Int): Boolean
        fun getPrimaryClip(callingPackage: String, userId: Int): Any?
    }

    /** 模拟 Android 12-13 签名的 IClipboard */
    interface MockIClipboardApi12 {
        fun setPrimaryClip(clip: Any, callingPackage: String, attributionTag: String?, userId: Int): Boolean
        fun getPrimaryClip(callingPackage: String, attributionTag: String?, userId: Int): Any?
    }

    /** 模拟 Android 14-15+ 签名的 IClipboard */
    interface MockIClipboardApi14 {
        fun setPrimaryClip(clip: Any, callingPackage: String, attributionTag: String?, userId: Int, deviceId: Int): Boolean
        fun getPrimaryClip(callingPackage: String, attributionTag: String?, userId: Int, deviceId: Int): Any?
    }

    @Test
    fun testMultiVersionMethodMatchingForSetClip() {
        val setClipExecuted = AtomicBoolean(false)

        val mockService14 = object : MockIClipboardApi14 {
            override fun setPrimaryClip(clip: Any, callingPackage: String, attributionTag: String?, userId: Int, deviceId: Int): Boolean {
                assertEquals("com.android.shell", callingPackage)
                assertEquals(0, userId)
                assertEquals(0, deviceId)
                setClipExecuted.set(true)
                return true
            }
            override fun getPrimaryClip(callingPackage: String, attributionTag: String?, userId: Int, deviceId: Int): Any? = "clip"
        }

        // 测试动态反射能正确匹配 5 参数版本
        val methods = mockService14.javaClass.methods.filter { it.name == "setPrimaryClip" }
        assertTrue("Should find setPrimaryClip method", methods.isNotEmpty())

        val targetMethod = methods.first()
        val paramTypes = targetMethod.parameterTypes
        val args = arrayOfNulls<Any>(paramTypes.size)
        args[0] = "fake_clip"
        for (i in 1 until paramTypes.size) {
            args[i] = when (paramTypes[i]) {
                String::class.java -> if (i == 1) "com.android.shell" else null
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }
        targetMethod.invoke(mockService14, *args)
        assertTrue("Android 14+ setPrimaryClip must execute via dynamic matching", setClipExecuted.get())
    }

    @Test
    fun testMultiVersionMethodMatchingForLegacyApi() {
        val setClipExecuted = AtomicBoolean(false)

        val mockService10 = object : MockIClipboardApi10 {
            override fun setPrimaryClip(clip: Any, callingPackage: String, userId: Int): Boolean {
                assertEquals("com.android.shell", callingPackage)
                assertEquals(0, userId)
                setClipExecuted.set(true)
                return true
            }
            override fun getPrimaryClip(callingPackage: String, userId: Int): Any? = "clip"
        }

        val methods = mockService10.javaClass.methods.filter { it.name == "setPrimaryClip" }
        assertTrue("Should find setPrimaryClip method", methods.isNotEmpty())

        val targetMethod = methods.first()
        val paramTypes = targetMethod.parameterTypes
        val args = arrayOfNulls<Any>(paramTypes.size)
        args[0] = "fake_clip"
        for (i in 1 until paramTypes.size) {
            args[i] = when (paramTypes[i]) {
                String::class.java -> if (i == 1) "com.android.shell" else null
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }
        targetMethod.invoke(mockService10, *args)
        assertTrue("Android 10 setPrimaryClip must execute via dynamic matching", setClipExecuted.get())
    }
}
