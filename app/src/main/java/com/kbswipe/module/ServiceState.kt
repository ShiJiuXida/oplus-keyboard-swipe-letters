package com.kbswipe.module

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Xposed 服务连接的进程内单例：模块 UI 进程与键盘宿主进程共用。
 * 服务就绪后可读写 LSPosed 提供的远程配置（RemotePreferences）。
 */
object ServiceState {

    private const val TAG = "SwipeKeys"

    private val started = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(XposedService?) -> Unit>()

    @Volatile
    var service: XposedService? = null
        private set

    /** 每个进程只注册一次 XposedServiceHelper 监听。 */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(bound: XposedService) {
                    Log.i(TAG, "xposed service bound: ${bound.frameworkName} ${bound.frameworkVersion}")
                    service = bound
                    for (listener in listeners) listener(bound)
                }

                override fun onServiceDied(dead: XposedService) {
                    Log.w(TAG, "xposed service died")
                    if (service === dead) service = null
                    for (listener in listeners) listener(null)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "registerListener failed", t)
        }
    }

    fun addListener(listener: (XposedService?) -> Unit, notifyImmediately: Boolean) {
        listeners.add(listener)
        if (notifyImmediately) listener(service)
    }

    fun removeListener(listener: (XposedService?) -> Unit) {
        listeners.remove(listener)
    }

    /** 读取远程配置，服务不可用时返回 null。 */
    fun prefs(): SharedPreferences? {
        val s = service ?: return null
        return try {
            s.getRemotePreferences(Config.PREFS_GROUP)
        } catch (t: Throwable) {
            Log.e(TAG, "getRemotePreferences failed", t)
            null
        }
    }
}
