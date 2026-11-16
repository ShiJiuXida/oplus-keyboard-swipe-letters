package com.kbswipe.module.hook

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

/**
 * 左下角功能键互换：把每个键盘底行最左边的两个功能键交换位置。
 *
 * 宿主把键盘布局放在 raw JSON 里，由 `com.oplus.keyboard.base.util.c.u(int)` 解析成
 * `com.oplus.keyboard.base.preset.d`（rowsInfo = ArrayList<Pair<行参数, ArrayList<preset.a>>>）。
 * 在该工厂方法返回后改写底行 ArrayList 的元素顺序，即可实现：
 * - 主键盘（子开关 1）：[符号][123] → [123][符号]
 * - 符号面板（子开关 2）：[返回][123] → [123][返回]
 * - 数字面板（子开关 2）：[符号][返回] → [返回][符号]
 *
 * 字段名是混淆产物：这里全部按「运行时值的类型」定位（ArrayList / KeyCode 枚举），
 * 不依赖 a/b/c 这类字段名，也不依赖泛型签名（Pair 的字段擦除后都是 Object）。
 */
object KeySwapPatcher {

    private const val DESIGN_CLASS = "com.oplus.keyboard.base.preset.d"
    private const val KEY_CLASS = "com.oplus.keyboard.base.preset.a"
    private const val KEYCODE_CLASS = "com.oplus.keyboard.base.enums.KeyCode"
    private const val FACTORY_CLASS = "com.oplus.keyboard.base.util.c"
    private const val FACTORY_METHOD = "u"

    /**
     * preset.a 构造器参数里属于「身份/样式」的下标：
     * 0=KeyCode、7=keyBackground、8=keyShadowBackground、9=stateId、10=keyIconShow、11=keyHasBackground。
     * 其余下标都是几何参数（宽高、字号、边距、touch 扩展、跨行等），换位时保留原槽位的值。
     */
    private val IDENTITY_INDEXES = setOf(0, 7, 8, 9, 10, 11)

    /** 安装布局工厂 Hook，返回是否安装成功。 */
    fun install(
        module: XposedInterface,
        classLoader: ClassLoader,
        swapMain: Boolean,
        swapPanel: Boolean,
    ): Boolean {
        if (!swapMain && !swapPanel) return false
        val factory = try {
            Class.forName(FACTORY_CLASS, false, classLoader)
        } catch (_: Throwable) {
            return false
        }
        val method = try {
            factory.getDeclaredMethod(FACTORY_METHOD, Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (_: Throwable) {
            return false
        }
        module.hook(method).intercept { chain ->
            val design = chain.proceed()
            try {
                patchDesign(design, swapMain, swapPanel)
            } catch (_: Throwable) {
                // 结构变化不应影响宿主，静默跳过
            }
            design
        }
        return true
    }

    /**
     * 交换一个键盘设计底行最左两个功能键。
     * 只在「第 1 键是符号/返回、第 2 键是 123（或面板里的符号+返回）」时生效，天然幂等。
     */
    private fun patchDesign(design: Any?, swapMain: Boolean, swapPanel: Boolean): Boolean {
        if (design == null || design.javaClass.name != DESIGN_CLASS) return false
        for (list in arrayListsOf(design)) {
            val rows = extractRows(list) ?: continue
            return swapBottomRow(rows.lastOrNull(), swapMain, swapPanel)
        }
        return false
    }

    /** 把 rowsInfo 逐行解析成键列表；任何一行不是「行对象 + 键 ArrayList」就返回 null。 */
    private fun extractRows(rowsInfo: ArrayList<*>): List<MutableList<Any>>? {
        if (rowsInfo.isEmpty()) return null
        val rows = ArrayList<MutableList<Any>>(rowsInfo.size)
        for (element in rowsInfo) {
            rows.add(keyListOf(element) ?: return null)
        }
        return rows
    }

    /** 行对象（kotlin.Pair，字段名被混淆）里找出装着 preset.a 的那个 ArrayList。 */
    private fun keyListOf(row: Any?): MutableList<Any>? {
        if (row == null) return null
        for (list in arrayListsOf(row)) {
            if (list.isEmpty()) continue
            if (list.any { it == null || it.javaClass.name != KEY_CLASS }) continue
            @Suppress("UNCHECKED_CAST")
            return list as MutableList<Any>
        }
        return null
    }

    private fun swapBottomRow(
        keys: MutableList<Any>?,
        swapMain: Boolean,
        swapPanel: Boolean,
    ): Boolean {
        if (keys == null || keys.size < 2) return false
        val left = keyCodeName(keys[0]) ?: return false
        val right = keyCodeName(keys[1]) ?: return false
        val matched = when {
            // 主键盘：符号 · 123 → 123 · 符号
            left == "SYMBOL" && right == "NUMBER" -> swapMain
            // 符号面板：返回 · 123 → 123 · 返回
            left == "BACK" && right == "NUMBER" -> swapPanel
            // 数字面板：符号 · 返回 → 返回 · 符号
            left == "SYMBOL" && right == "BACK" -> swapPanel
            else -> false
        }
        if (!matched) return false
        return swapIdentityKeepingGeometry(keys)
    }

    /**
     * 只交换「功能/样式」字段，保留每个槽位自己的尺寸。
     * 键对象是 final 字段，只能按原构造器重建：
     * - 身份字段（KeyCode / 背景 / 阴影 / stateId / 图标开关）取对方的值
     * - 几何字段（宽高、字号、边距、touch 扩展、跨行、扩展宽度）保留本槽位的值
     */
    private fun swapIdentityKeepingGeometry(keys: MutableList<Any>): Boolean {
        val first = keys[0]
        val second = keys[1]
        val cls = first.javaClass
        if (second.javaClass != cls) return false
        val fields = cls.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        // 防御：字段顺序必须与构造器参数一一对应，且第 0 个是 KeyCode，否则宁可不换
        if (fields.isEmpty() || fields[0].type.name != KEYCODE_CLASS) return false
        val ctor = cls.declaredConstructors.firstOrNull {
            it.parameterCount == fields.size &&
                it.parameterTypes[0].name == KEYCODE_CLASS
        } ?: return false
        ctor.isAccessible = true
        fields.forEach { it.isAccessible = true }
        val a = rebuild(ctor, fields, first, second) ?: return false
        val b = rebuild(ctor, fields, second, first) ?: return false
        keys[0] = a
        keys[1] = b
        return true
    }

    /** own 提供几何值，other 提供身份值。 */
    private fun rebuild(
        ctor: java.lang.reflect.Constructor<*>,
        fields: List<java.lang.reflect.Field>,
        own: Any,
        other: Any,
    ): Any? {
        val args = arrayOfNulls<Any?>(fields.size)
        for (index in fields.indices) {
            val source = if (index in IDENTITY_INDEXES) other else own
            args[index] = try {
                fields[index].get(source)
            } catch (_: Throwable) {
                return null
            }
        }
        return try {
            ctor.newInstance(*args)
        } catch (_: Throwable) {
            null
        }
    }

    /** 读键对象上的 KeyCode 枚举名。 */
    private fun keyCodeName(key: Any): String? {
        var cls: Class<*>? = key.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                val value = try {
                    field.get(key)
                } catch (_: Throwable) {
                    null
                } ?: continue
                if (value.javaClass.name == KEYCODE_CLASS && value is Enum<*>) return value.name
            }
            cls = cls.superclass
        }
        return null
    }

    /** 收集对象实例上所有非静态 ArrayList 字段的值（含父类）。 */
    private fun arrayListsOf(target: Any): List<ArrayList<*>> {
        val result = ArrayList<ArrayList<*>>(2)
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                val value = try {
                    field.get(target)
                } catch (_: Throwable) {
                    null
                }
                if (value is ArrayList<*>) result.add(value)
            }
            cls = cls.superclass
        }
        return result
    }
}
