package com.kbswipe.module

import org.json.JSONObject

/** 配置常量、默认映射与 JSON 编解码。 */
object Config {

    const val PREFS_GROUP = "config"
    const val KEY_ENABLED = "enabled"
    const val KEY_MAP_CN = "map_cn"
    const val KEY_MAP_EN = "map_en"

    /** 左下角功能键互换：主键盘（符号 ⇄ 123）。 */
    const val KEY_SWAP_MAIN = "swap_main"
    const val DEFAULT_SWAP_MAIN = true

    /** 左下角功能键互换：符号 / 数字面板（返回 ⇄ 123、符号 ⇄ 返回）。 */
    const val KEY_SWAP_PANEL = "swap_panel"
    const val DEFAULT_SWAP_PANEL = true

    /** 键盘字母键顺序（qwerty 三行拼接）。 */
    const val LETTERS = "qwertyuiopasdfghjklzxcvbnm"

    /** 最大自定义符号长度（字符数）。 */
    const val MAX_SYMBOL_LENGTH = 8

    /** 中文 26 键（拼音/五笔）默认上滑符号。 */
    val CN_DEFAULTS = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "-", "/", "：", "；", "（", "）", "～", "“", "”",
        "@", ".", "#", "、", "？", "！", "…",
    )

    /** 英文 26 键默认上滑符号。 */
    val EN_DEFAULTS = listOf(
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
        "-", "/", ":", ";", "(", ")", "~", "'", "\"",
        "@", "_", "#", "&", "?", "!", "…",
    )

    fun defaultFor(cn: Boolean, letter: Char): String {
        val index = LETTERS.indexOf(letter.lowercaseChar())
        if (index < 0) return ""
        return (if (cn) CN_DEFAULTS else EN_DEFAULTS)[index]
    }

    /**
     * 编码映射。值为 null 的字母不写入（表示使用默认值），空串表示禁用该键上滑。
     */
    fun encode(map: Map<Char, String?>): String {
        val obj = JSONObject()
        for ((letter, value) in map) {
            if (value != null) obj.put(letter.toString(), value)
        }
        return obj.toString()
    }

    /** 解码映射。缺省或空串的 JSON 返回空 Map。 */
    fun decode(raw: String?): Map<Char, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<Char, String>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val letter = k.singleOrNull()?.lowercaseChar() ?: continue
                if (letter !in 'a'..'z') continue
                result[letter] = obj.optString(k, "")
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return result
    }

    /**
     * 导出为对外 JSON（version=3，含 enabled/cn/en 分区）。
     * 值为 null 的字母不写入（表示默认），空串表示禁用。
     */
    fun exportJson(enabled: Boolean, cn: Map<Char, String?>, en: Map<Char, String?>): String {
        val root = JSONObject()
        root.put("version", 3)
        root.put("enabled", enabled)
        root.put("cn", sectionToJson(cn))
        root.put("en", sectionToJson(en))
        return root.toString(2)
    }

    data class ImportData(
        val enabled: Boolean,
        val cn: Map<Char, String>,
        val en: Map<Char, String>,
    )

    /** 解析导入 JSON；格式错误返回 null。未知分区忽略。 */
    fun parseImport(raw: String): ImportData? {
        if (raw.isBlank()) return null
        return try {
            val root = JSONObject(raw)
            ImportData(
                enabled = root.optBoolean("enabled", true),
                cn = sectionFromJson(root.optJSONObject("cn")),
                en = sectionFromJson(root.optJSONObject("en")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sectionToJson(map: Map<Char, String?>): JSONObject {
        val obj = JSONObject()
        for ((letter, value) in map) {
            if (value != null) obj.put(letter.toString(), value)
        }
        return obj
    }

    private fun sectionFromJson(obj: JSONObject?): Map<Char, String> {
        if (obj == null) return emptyMap()
        val result = LinkedHashMap<Char, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val letter = k.singleOrNull()?.lowercaseChar() ?: continue
            if (letter !in 'a'..'z') continue
            result[letter] = obj.optString(k, "")
        }
        return result
    }
}
