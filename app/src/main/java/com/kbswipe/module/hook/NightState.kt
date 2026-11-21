package com.kbswipe.module.hook

import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 读取宿主当前的「白天 / 黑夜」状态。
 *
 * 宿主自己的判定链（1.9.25 实测）：
 *
 * ```
 * DayNightMode 枚举：MODE_FOLLOW_SYS / MODE_NIGHT_YES / MODE_NIGHT_NO
 *   ↓ 设置项 key_day_night_mode
 * theme.util.e.e()            → 原始设置
 * theme.util.e.m(DayNightMode) → 解析成最终模式
 *      · 主题预览强制夜间时直接 NIGHT_YES
 *      · FOLLOW_SYS 时读 Resources.configuration.uiMode & 0x30 == 0x20
 *   ↓
 * DayNightMode.isNight()      → boolean
 * ```
 *
 * 直接读系统 uiMode 会漏掉「键盘内单独设为夜间/白天」的情况，所以优先调用宿主的解析方法；
 * 任何一步失败都逐级降级，最终兜底用系统配置，保证不会因为宿主改名而失效。
 *
 * 结果做了短 TTL 缓存（角标绘制可能在滚动/切换时密集触发）。
 */
internal object NightState {

    private const val TAG = "SwipeKeys"
    private const val TTL_MS = 400L

    private const val THEME_UTIL_CLASS = "com.oplus.keyboard.base.theme.util.e"
    private const val DAY_NIGHT_ENUM = "com.oplus.keyboard.base.theme.enums.DayNightMode"

    @Volatile
    private var resolver: Resolver? = null

    @Volatile
    private var resolverMissing = false

    @Volatile
    private var lastCheckAt = 0L

    @Volatile
    private var lastValue = false

    /** 当前是否夜间。线程安全、开销有界。 */
    fun isNight(classLoader: ClassLoader): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckAt < TTL_MS) return lastValue
        val value = try {
            (resolver ?: buildResolver(classLoader))?.invoke() ?: systemNight()
        } catch (_: Throwable) {
            systemNight()
        }
        lastValue = value
        lastCheckAt = now
        return value
    }

    /** 解析器只构建一次；失败则记住不再重试，避免每帧反射。 */
    private fun buildResolver(classLoader: ClassLoader): Resolver? {
        if (resolverMissing) return null
        val built = tryBuildResolver(classLoader)
        if (built == null) {
            resolverMissing = true
            Log.i(TAG, "night state: falling back to system uiMode")
        } else {
            resolver = built
        }
        return built
    }

    private fun tryBuildResolver(classLoader: ClassLoader): Resolver? {
        val enumClass = try {
            Class.forName(DAY_NIGHT_ENUM, false, classLoader)
        } catch (_: Throwable) {
            null
        } ?: return null

        // 1) 首选：e.m(DayNightMode) → 解析后的 DayNightMode
        val utilClass = try {
            Class.forName(THEME_UTIL_CLASS, false, classLoader)
        } catch (_: Throwable) {
            null
        }
        if (utilClass != null) {
            val resolveMethod = utilClass.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == enumClass &&
                    it.returnType == enumClass
            }
            val isNightMethod = enumClass.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            if (resolveMethod != null && isNightMethod != null) {
                return Resolver {
                    val mode = invokeMethod(resolveMethod, null, null)
                    (invokeMethod(isNightMethod, mode, null) as? Boolean) ?: systemNight()
                }
            }
        }

        // 2) 次选：无参静态 e.e() → 原始 DayNightMode，FOLLOW_SYS 时自己看 uiMode
        if (utilClass != null) {
            val rawMethod = utilClass.declaredMethods.firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    it.returnType == enumClass
            }
            val isNightMethod = enumClass.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            if (rawMethod != null) {
                return Resolver {
                    val mode = invokeMethod(rawMethod, null, null)
                    val night = if (isNightMethod != null) {
                        (invokeMethod(isNightMethod, mode, null) as? Boolean) ?: false
                    } else {
                        mode?.toString() == "MODE_NIGHT_YES"
                    }
                    val followSys = mode?.toString() == "MODE_FOLLOW_SYS"
                    if (followSys) systemNight() else night
                }
            }
        }
        return null
    }

    private fun invokeMethod(method: Method, receiver: Any?, arg: Any?): Any? = try {
        method.isAccessible = true
        if (method.parameterCount == 0) method.invoke(receiver) else method.invoke(receiver, arg)
    } catch (_: Throwable) {
        null
    }

    /** 兜底：系统夜间模式。 */
    private fun systemNight(): Boolean = try {
        val uiMode = android.content.res.Resources.getSystem().configuration.uiMode
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    } catch (_: Throwable) {
        false
    }

    private fun interface Resolver {
        fun invoke(): Boolean
    }
}
