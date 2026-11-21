package com.kbswipe.module.hook

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

/**
 * 替换上滑字符（键帽右上角标）颜色，白天 / 黑夜两套跟随切换。
 *
 * 宿主 1.9.25 的角标颜色链路（实测 smali）：
 *
 * ```
 * KeySkin.markColor ──┐
 *                     ├─(生成主题时写入 dataStore)─► 主题属性 "colorKeyMark"
 * 主题包 marker/a ────┘
 *
 * 绘制时（input/view/body/t 等，每帧）：
 *   theme.b.g(keyboardType, "colorKeyMark") → Integer → paint.setColor(...)
 * ```
 *
 * **只 hook `KeySkin.getMarkColor()` 不够**：它只在主题构建时被调用一次，
 * 之后绘制读的是主题缓存，改 getter 对已生成的主题无效（v1.13 实测不生效）。
 *
 * 主 hook 放在**读取点** `com.oplus.keyboard.base.theme.b.g(KeyboardType, String)`：
 * 每次绘制角标都会经过这里，且 key 恰为 "colorKeyMark"，可以精确只改角标色、
 * 不动其它主题属性（键帽文字色、背景色等）。
 *
 * 日夜切换：读取点的调用时机正好在绘制前，直接按 [NightState] 的当前状态选颜色，
 * 切换系统深浅色后无需重启输入法即可生效。
 */
object MarkColorPatcher {

    private const val TAG = "SwipeKeys"

    /** 主题属性读取器（未混淆类名）。 */
    private const val THEME_CLASS = "com.oplus.keyboard.base.theme.b"
    private const val KEYBOARD_TYPE_CLASS = "com.oplus.keyboard.base.enums.KeyboardType"

    /** 只拦这一个属性，其它主题属性原样放行。 */
    private const val COLOR_KEY = "colorKeyMark"

    /** 兜底：主题构建时的来源 getter（旧版本专用）。 */
    private val KEY_SKIN_CLASSES = arrayOf(
        "com.oplus.keyboard.base.theme.data.KeySkin",
        "com.oplus.keyboard.base.theme.data.skin.KeySkin",
    )
    private const val GETTER = "getMarkColor"

    /** 开关与两套颜色放在对象字段上：hook 只装一次，配置变化时热更新。 */
    @Volatile
    var enabled: Boolean = false

    /** 白天（浅色主题）角标色。 */
    @Volatile
    var lightColor: Int = 0

    /** 黑夜（深色主题）角标色。 */
    @Volatile
    var darkColor: Int = 0

    @Volatile
    private var classLoaderRef: ClassLoader? = null

    /**
     * 安装角标色 hook，返回命中的 hook 点数（0 表示失败）。
     */
    fun install(module: XposedInterface, classLoader: ClassLoader, lightColor: Int, darkColor: Int): Int {
        classLoaderRef = classLoader
        this.lightColor = lightColor
        this.darkColor = darkColor
        var hits = 0
        if (hookThemeReader(module, classLoader)) hits++
        if (hookKeySkinGetter(module, classLoader)) hits++
        return hits
    }

    /** 按当前日夜状态返回该用的颜色。 */
    private fun currentColor(): Int {
        val loader = classLoaderRef ?: return lightColor
        return if (NightState.isNight(loader)) darkColor else lightColor
    }

    /**
     * 主 hook：主题属性读取点，绘制每帧都会调用。
     * 命中 "colorKeyMark" 且开关打开时返回自定义色，否则走原逻辑。
     */
    private fun hookThemeReader(module: XposedInterface, classLoader: ClassLoader): Boolean {
        val themeClass = try {
            Class.forName(THEME_CLASS, false, classLoader)
        } catch (_: Throwable) {
            return false
        }
        val keyboardTypeClass = try {
            Class.forName(KEYBOARD_TYPE_CLASS, false, classLoader)
        } catch (_: Throwable) {
            return false
        }
        val methods = try {
            themeClass.declaredMethods.filter {
                !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == keyboardTypeClass &&
                    it.parameterTypes[1] == String::class.java &&
                    it.returnType == Integer::class.java
            }
        } catch (_: Throwable) {
            return false
        }
        var hooked = 0
        for (method in methods) {
            try {
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val key = chain.getArg(1) as? String
                    if (enabled && key == COLOR_KEY) {
                        Integer.valueOf(currentColor())
                    } else {
                        chain.proceed()
                    }
                }
                hooked++
            } catch (_: Throwable) {
                // 单个方法失败不影响其它
            }
        }
        return hooked > 0
    }

    /** 兜底 hook：主题构建时读取的 KeySkin.getMarkColor()。 */
    private fun hookKeySkinGetter(module: XposedInterface, classLoader: ClassLoader): Boolean {
        for (className in KEY_SKIN_CLASSES) {
            val cls = try {
                Class.forName(className, false, classLoader)
            } catch (_: Throwable) {
                continue
            }
            val getter = try {
                cls.getDeclaredMethod(GETTER).apply { isAccessible = true }
            } catch (_: Throwable) {
                continue
            }
            if (getter.returnType != Int::class.javaPrimitiveType) continue
            try {
                module.hook(getter).intercept { chain ->
                    if (enabled) currentColor() else chain.proceed()
                }
                return true
            } catch (_: Throwable) {
                // 试下一个候选
            }
        }
        return false
    }
}
