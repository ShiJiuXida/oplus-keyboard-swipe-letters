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

    /** 隐藏剪切板 / 常用语顶栏最右侧的设置齿轮。 */
    const val KEY_HIDE_CLIP_SETTING = "hide_clip_setting"
    const val DEFAULT_HIDE_CLIP_SETTING = true

    /** 替换上滑字符（键帽角标）颜色。 */
    const val KEY_MARK_COLOR_ENABLED = "mark_color_enabled"
    const val DEFAULT_MARK_COLOR_ENABLED = false

    /** 白天（浅色主题）角标颜色（ARGB）。 */
    const val KEY_MARK_COLOR_LIGHT = "mark_color_light"

    /** 黑夜（深色主题）角标颜色（ARGB）。 */
    const val KEY_MARK_COLOR_DARK = "mark_color_dark"

    /** v1.13 的单色配置键，仅用于迁移到日夜两色。 */
    const val KEY_MARK_COLOR_LEGACY = "mark_color"

    /** 白天默认：橙红，浅底上对比清晰。 */
    const val DEFAULT_MARK_COLOR_LIGHT = 0xFFE8451C.toInt()

    /** 黑夜默认：青绿，深底上不刺眼但可辨。 */
    const val DEFAULT_MARK_COLOR_DARK = 0xFF4DD0C4.toInt()

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
     * 导出为对外 JSON（version=5，含 enabled / 换位 / 日夜角标色 / cn / en 分区）。
     * 值为 null 的字母不写入（表示默认），空串表示禁用。
     */
    fun exportJson(
        enabled: Boolean,
        cn: Map<Char, String?>,
        en: Map<Char, String?>,
        swapMain: Boolean = DEFAULT_SWAP_MAIN,
        swapPanel: Boolean = DEFAULT_SWAP_PANEL,
        hideClipSetting: Boolean = DEFAULT_HIDE_CLIP_SETTING,
        markColorEnabled: Boolean = DEFAULT_MARK_COLOR_ENABLED,
        markColorLight: Int = DEFAULT_MARK_COLOR_LIGHT,
        markColorDark: Int = DEFAULT_MARK_COLOR_DARK,
    ): String {
        val root = JSONObject()
        root.put("version", 5)
        root.put("enabled", enabled)
        root.put("swap_main", swapMain)
        root.put("swap_panel", swapPanel)
        root.put("hide_clip_setting", hideClipSetting)
        root.put("mark_color_enabled", markColorEnabled)
        root.put("mark_color_light", colorToHex(markColorLight))
        root.put("mark_color_dark", colorToHex(markColorDark))
        root.put("cn", sectionToJson(cn))
        root.put("en", sectionToJson(en))
        return root.toString(2)
    }

    data class ImportData(
        val enabled: Boolean,
        val cn: Map<Char, String>,
        val en: Map<Char, String>,
        /** 旧版配置没有这些字段，null 表示「文件未提供、保持当前值」。 */
        val swapMain: Boolean? = null,
        val swapPanel: Boolean? = null,
        val hideClipSetting: Boolean? = null,
        val markColorEnabled: Boolean? = null,
        val markColorLight: Int? = null,
        val markColorDark: Int? = null,
    )

    /**
     * 解析导入 JSON；格式错误返回 null。未知分区忽略。
     *
     * 兼容范围：
     * - v4（当前）：enabled / swap_* / hide_clip_setting / mark_color_* / cn / en
     * - v3 及更早（1.12 及以前导出的文件）：只有 enabled / cn / en，
     *   缺失的开关字段为 null，由调用方保持界面现值；
     * - 无 version 字段、或顶层直接是 {cn:{...},en:{...}} 的手写文件；
     * - 顶层直接是字母映射（{"q":"1",...}）时按 cn 处理；
     * - UTF-8 BOM、首尾空白。
     */
    fun parseImport(raw: String): ImportData? {
        val text = raw.removePrefix("\uFEFF").trim()
        if (text.isEmpty()) return null
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return null
        }
        val cnObj = root.optJSONObject("cn")
        val enObj = root.optJSONObject("en")
        var cn = sectionFromJson(cnObj)
        val en = sectionFromJson(enObj)

        // 顶层直接就是字母映射（没有任何已知分区）→ 当作中文配置
        if (cn.isEmpty() && en.isEmpty() && looksLikeLetterMap(root)) {
            cn = sectionFromJson(root)
        }
        if (cn.isEmpty() && en.isEmpty() && cnObj == null && enObj == null) {
            // 既没有 cn/en，也不是字母映射：不是本模块的配置文件
            return null
        }
        // 单色（v1.13）配置迁移：没有日夜两色时，用旧值同时填两套
        val legacy = root.optStringOrNull(KEY_MARK_COLOR_LEGACY)?.let { parseHexColor(it) }
        return ImportData(
            enabled = root.optBooleanOrNull("enabled") ?: true,
            cn = cn,
            en = en,
            swapMain = root.optBooleanOrNull("swap_main"),
            swapPanel = root.optBooleanOrNull("swap_panel"),
            hideClipSetting = root.optBooleanOrNull("hide_clip_setting"),
            markColorEnabled = root.optBooleanOrNull("mark_color_enabled"),
            markColorLight = root.optStringOrNull(KEY_MARK_COLOR_LIGHT)?.let { parseHexColor(it) }
                ?: legacy,
            markColorDark = root.optStringOrNull(KEY_MARK_COLOR_DARK)?.let { parseHexColor(it) }
                ?: legacy,
        )
    }

    /** 顶层键里是否至少有一个 a-z 单字母（用于识别「裸映射」格式）。 */
    private fun looksLikeLetterMap(obj: JSONObject): Boolean {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.length == 1 && k[0].lowercaseChar() in 'a'..'z') return true
        }
        return false
    }

    /** #RRGGBB 或 #AARRGGBB → Int；无法识别返回 null。 */
    fun parseHexColor(raw: String): Int? {
        val text = raw.trim().removePrefix("#")
        if (text.length != 6 && text.length != 8) return null
        val value = text.toLongOrNull(16) ?: return null
        return if (text.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    /** Int ARGB → "#AARRGGBB"（导出用，alpha 也保留）。 */
    fun colorToHex(color: Int): String = String.format("#%08X", color)

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

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
