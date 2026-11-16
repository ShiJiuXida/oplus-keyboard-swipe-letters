package com.kbswipe.module

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kbswipe.module.hook.KeySwapPatcher
import com.kbswipe.module.hook.SwipePatcher
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块入口，运行于小布输入法进程。
 *
 * 宿主进程内的配置读取走框架注入的 XposedInterface.getRemotePreferences()
 * （LSPosed 只会把服务 binder 投递给模块自身进程，不会投递给宿主进程）。
 *
 * 两个补丁：
 * - [SwipePatcher]：改写 preset.c 里的字母键上滑符号映射（静态 Map，直接改内容）
 * - [KeySwapPatcher]：Hook 布局工厂，交换底行最左两个功能键
 *
 * 功耗策略：
 * - 只有加载了 preset 布局类的进程才读取远程配置并注册监听，其它进程直接跳过；
 * - 配置变化的重启做了防抖合并，连续保存只触发一次进程重启。
 */
class ModuleEntry : XposedModule() {

    companion object {
        private const val TAG = "SwipeKeys"
        private const val TARGET_PACKAGE = "com.oplus.keyboard"
        private const val RESTART_DEBOUNCE_MS = 600L
        private val listenerRegistered = AtomicBoolean(false)
        private val keySwapInstalled = AtomicBoolean(false)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        val classLoader = param.classLoader

        // 输入法有多个进程，只有加载了 preset 布局类的进程才需要工作。
        if (!SwipePatcher.isPresetPresent(classLoader)) {
            log(Log.INFO, TAG, "no preset class in this process, skip")
            return
        }

        val prefs = try {
            getRemotePreferences(Config.PREFS_GROUP)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "getRemotePreferences failed", t)
            return
        }
        if (!prefs.getBoolean(Config.KEY_ENABLED, true)) {
            log(Log.INFO, TAG, "module disabled by user, keeping defaults")
            return
        }
        log(Log.INFO, TAG, "package ready: ${param.packageName}")

        try {
            applyConfig(classLoader, prefs)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "applyConfig failed", t)
        }
        registerConfigListener(classLoader, prefs)
    }

    private fun applyConfig(classLoader: ClassLoader, prefs: SharedPreferences) {
        val cn = Config.decode(prefs.getString(Config.KEY_MAP_CN, null))
        val en = Config.decode(prefs.getString(Config.KEY_MAP_EN, null))
        SwipePatcher.patch(classLoader, cn, en)

        val swapMain = prefs.getBoolean(Config.KEY_SWAP_MAIN, Config.DEFAULT_SWAP_MAIN)
        val swapPanel = prefs.getBoolean(Config.KEY_SWAP_PANEL, Config.DEFAULT_SWAP_PANEL)
        if (swapMain || swapPanel) {
            installKeySwap(classLoader, swapMain, swapPanel)
        }
    }

    /** 左下角功能键互换：布局工厂 Hook 每个进程只装一次。 */
    private fun installKeySwap(classLoader: ClassLoader, swapMain: Boolean, swapPanel: Boolean) {
        if (!keySwapInstalled.compareAndSet(false, true)) return
        val ok = try {
            KeySwapPatcher.install(this, classLoader, swapMain, swapPanel)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "keyswap hook failed", t)
            false
        }
        log(
            Log.INFO,
            TAG,
            if (ok) "keyswap hook installed (main=$swapMain, panel=$swapPanel)"
            else "keyswap hook unavailable (layout factory not found)",
        )
    }

    /** 配置变化：防抖后重新应用并重启输入法进程。 */
    private fun registerConfigListener(classLoader: ClassLoader, prefs: SharedPreferences) {
        if (!listenerRegistered.compareAndSet(false, true)) return
        val handler = Handler(Looper.getMainLooper())
        val restart = Runnable {
            try {
                applyConfig(classLoader, prefs)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "re-apply failed", t)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            log(Log.INFO, TAG, "config changed, debounced re-apply and restart")
            handler.removeCallbacks(restart)
            handler.postDelayed(restart, RESTART_DEBOUNCE_MS)
        }
    }
}
