package com.kbswipe.module

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块入口，运行于小布输入法进程。
 *
 * 宿主进程内的配置读取走框架注入的 XposedInterface.getRemotePreferences()
 * （LSPosed 只会把服务 binder 投递给模块自身进程，不会投递给宿主进程）。
 * 配置就绪后直接改写键盘的字母布局映射（见 [SwipePatcher]）；配置变化时
 * 重新应用并结束当前进程，使输入法以新布局重启。
 */
class ModuleEntry : XposedModule() {

    companion object {
        private const val TAG = "SwipeKeys"
        private const val TARGET_PACKAGE = "com.oplus.keyboard"
        private val listenerRegistered = AtomicBoolean(false)
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(Log.INFO, TAG, "module loaded in process ${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        log(Log.INFO, TAG, "package ready: ${param.packageName}")
        val classLoader = param.classLoader
        val prefs = try {
            getRemotePreferences(Config.PREFS_GROUP)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "getRemotePreferences failed", t)
            return
        }
        try {
            applyConfig(classLoader, prefs)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "applyConfig failed", t)
        }
        if (listenerRegistered.compareAndSet(false, true)) {
            prefs.registerOnSharedPreferenceChangeListener { _, _ ->
                log(Log.INFO, TAG, "config changed, re-applying and restarting process")
                try {
                    applyConfig(classLoader, prefs)
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "re-apply failed", t)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun applyConfig(classLoader: ClassLoader, prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(Config.KEY_ENABLED, true)
        if (!enabled) {
            log(Log.INFO, TAG, "module disabled by user, keeping defaults")
            return
        }
        val cn = Config.decode(prefs.getString(Config.KEY_MAP_CN, null))
        val en = Config.decode(prefs.getString(Config.KEY_MAP_EN, null))
        SwipePatcher.patch(classLoader, cn, en)
    }
}
