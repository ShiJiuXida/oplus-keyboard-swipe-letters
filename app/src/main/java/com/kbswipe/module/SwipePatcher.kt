package com.kbswipe.module

import java.lang.reflect.Modifier

/**
 * 修改 com.oplus.keyboard.base.preset.c 中的字母键符号映射。
 *
 * 该类静态持有各键盘布局的 HashMap<KeyCode, String[]>；字母键的值是
 * [主标签, 上滑符号]，键盘按键对象（entity.f.t）与角标绘制都直接读取
 * 该数组的第二元素。这里通过反射定位所有「主标签为大写字母」的布局映射
 * （英文 f11903b、中文拼音 f11912m、五笔 x 等），并把字母键的第二元素
 * 替换为用户配置值。字段名是混淆产物，因此按内容识别而非按字段名。
 */
object SwipePatcher {

    private const val LETTER_KEYS = "QWERTYUIOPASDFGHJKLZXCVBNM"
    private const val CN_PROBE_KEYS = "DFGHJKL"

    /**
     * 在目标进程中应用映射。cn/en 为 null 时对应模式不做任何修改。
     * 值语义与 [Config.decode] 一致：缺省=默认、空串=禁用。
     */
    fun patch(classLoader: ClassLoader, cn: Map<Char, String>?, en: Map<Char, String>?) {
        if (cn.isNullOrEmpty() && en.isNullOrEmpty()) return
        val presetClass = Class.forName("com.oplus.keyboard.base.preset.c", true, classLoader)
        val keyCodeClass = Class.forName(
            "com.oplus.keyboard.base.enums.KeyCode", true, classLoader,
        )
        @Suppress("UNCHECKED_CAST")
        val keyCodeEnum = keyCodeClass as Class<out Enum<*>>
        val letterEnums = LETTER_KEYS.map { ch -> java.lang.Enum.valueOf(keyCodeEnum, ch.toString()) }

        var patchedLayouts = 0
        for (field in presetClass.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (!Map::class.java.isAssignableFrom(field.type)) continue
            field.isAccessible = true
            val map = field.get(null) as? Map<*, *> ?: continue
            val entries = collectLetterEntries(map, letterEnums) ?: continue
            val isCn = entries.any { (_, index) ->
                val letter = LETTER_KEYS[index]
                CN_PROBE_KEYS.contains(letter) && hasFullWidth(map[letterEnums[index]])
            }
            val config = (if (isCn) cn else en) ?: continue
            var applied = 0
            for ((letter, index) in entries) {
                val custom = config[letter.lowercaseChar()] ?: continue
                val array = map[letterEnums[index]] as Array<*>
                java.lang.reflect.Array.set(array, 1, custom)
                applied++
            }
            if (applied > 0) patchedLayouts++
        }
        android.util.Log.i(
            "SwipeKeys",
            "patched $patchedLayouts letter layouts (cn=${cn?.size ?: 0}, en=${en?.size ?: 0})",
        )
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
