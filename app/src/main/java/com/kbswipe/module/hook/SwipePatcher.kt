package com.kbswipe.module.hook

import java.lang.reflect.Modifier

/**
 * 修改 com.oplus.keyboard.base.preset.c 中的字母键符号映射。
 *
 * 该类静态持有各键盘布局的 HashMap<KeyCode, String[]>；字母键的值是
 * [主标签, 上滑符号]，键盘按键对象（entity.f.t）与角标绘制都直接读取
 * 该数组的第二元素。这里通过反射定位所有「主标签为大写字母」的布局映射
 * （英文、中文拼音、五笔等），并把字母键的第二元素替换为用户配置值。
 * 字段名是混淆产物，因此按内容识别而非按字段名。
 *
 * 跨版本注意（宿主 1.9.18 → 1.9.25）：
 * - 静态映射类名与结构未变，但布局数量明显变多（新增维吾尔语、藏语等十余套），
 *   旧实现「找齐 3 张就停」会漏掉部分字母布局；这里改为**遍历全部静态 Map**，
 *   以内容判定字母布局，不再依赖数量。
 * - 语言判定沿用「D/F/G/H/J/K/L 的上滑符号是否含全角字符」：中文布局用全角（：；（）
 *   ～“”），英文用半角（:;()~'"），两版实测一致。
 */
object SwipePatcher {

    private const val PRESET_CLASS_NAME = "com.oplus.keyboard.base.preset.c"
    private const val KEY_CODE_CLASS_NAME = "com.oplus.keyboard.base.enums.KeyCode"
    private const val LETTER_KEYS = "QWERTYUIOPASDFGHJKLZXCVBNM"
    private const val CN_PROBE_KEYS = "DFGHJKL"

    /** 进程内是否加载了 preset 布局类（不触发初始化，开销极小）。 */
    fun isPresetPresent(classLoader: ClassLoader): Boolean =
        try {
            Class.forName(PRESET_CLASS_NAME, false, classLoader)
            true
        } catch (_: Throwable) {
            false
        }

    /**
     * 在目标进程中应用映射。cn/en 为 null 时对应模式不做任何修改。
     * 值语义与 [com.kbswipe.module.Config.decode] 一致：缺省=默认、空串=禁用。
     * 返回被改写的布局数；-1 表示该进程未加载 preset/KeyCode 类。
     */
    fun patch(classLoader: ClassLoader, cn: Map<Char, String>?, en: Map<Char, String>?): Int {
        val presetClass = try {
            Class.forName(PRESET_CLASS_NAME, true, classLoader)
        } catch (_: Throwable) {
            return -1
        }
        val keyCodeClass = try {
            Class.forName(KEY_CODE_CLASS_NAME, true, classLoader)
        } catch (_: Throwable) {
            return -1
        }
        if (cn.isNullOrEmpty() && en.isNullOrEmpty()) return 0

        @Suppress("UNCHECKED_CAST")
        val keyCodeEnum = keyCodeClass as Class<out Enum<*>>
        val letterEnums = LETTER_KEYS.map { ch -> java.lang.Enum.valueOf(keyCodeEnum, ch.toString()) }

        var patchedLayouts = 0
        var letterLayouts = 0
        for (field in presetClass.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (!Map::class.java.isAssignableFrom(field.type)) continue
            field.isAccessible = true
            val map = try {
                field.get(null) as? Map<*, *>
            } catch (_: Throwable) {
                null
            } ?: continue
            val entries = collectLetterEntries(map, letterEnums) ?: continue
            letterLayouts++
            val isCn = entries.any { (_, index) ->
                val letter = LETTER_KEYS[index]
                CN_PROBE_KEYS.contains(letter) && hasFullWidth(map[letterEnums[index]])
            }
            val config = (if (isCn) cn else en) ?: continue
            var applied = 0
            for ((letter, index) in entries) {
                val custom = config[letter.lowercaseChar()] ?: continue
                val array = map[letterEnums[index]] as? Array<*> ?: continue
                try {
                    java.lang.reflect.Array.set(array, 1, custom)
                    applied++
                } catch (_: Throwable) {
                    // 单个键写失败不影响其它键
                }
            }
            if (applied > 0) patchedLayouts++
        }
        android.util.Log.i(
            "SwipeKeys",
            "patched $patchedLayouts/$letterLayouts letter layouts (cn=${cn?.size ?: 0}, en=${en?.size ?: 0})",
        )
        return patchedLayouts
    }

    private fun hasFullWidth(value: Any?): Boolean {
        val text = (value as? Array<*>)?.getOrNull(1) as? String ?: return false
        return text.any { it.code > 0x7F }
    }

    /** 校验并收集该映射里的字母键，不是字母布局则返回 null。 */
    private fun collectLetterEntries(
        map: Map<*, *>,
        letterEnums: List<Enum<*>>,
    ): List<Pair<Char, Int>>? {
        val entries = ArrayList<Pair<Char, Int>>(26)
        for (index in LETTER_KEYS.indices) {
            val ch = LETTER_KEYS[index]
            val value = map[letterEnums[index]] as? Array<*> ?: return null
            if (value.size < 2) return null
            val primary = value[0] as? String ?: return null
            if (!primary.equals(ch.toString(), ignoreCase = true)) return null
            entries.add(ch to index)
        }
        return if (entries.isEmpty()) null else entries
    }
}
