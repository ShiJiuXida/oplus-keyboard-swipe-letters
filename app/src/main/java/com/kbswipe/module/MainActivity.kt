package com.kbswipe.module

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CouiTheme {
                MainScreen()
            }
        }
    }
}

private val KEY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

private const val PROJECT_URL = "https://github.com/ShiJiuXida/oplus-keyboard-swipe-letters"

/** 引用过的开源库（二级菜单项）。 */
private data class ReferenceItem(val name: String, val url: String)

private val REFERENCES = listOf(
    ReferenceItem("COUI-Kit（界面框架）", "https://github.com/ItosEO/COUI-Kit"),
    ReferenceItem("libxposed/api（模块 API）", "https://github.com/libxposed/api"),
    ReferenceItem("libxposed/service（框架服务）", "https://github.com/libxposed/service"),
)

@Composable
private fun MainScreen() {
    var service by remember { mutableStateOf<XposedService?>(null) }
    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = { service = it }
        ServiceState.addListener(listener, notifyImmediately = true)
        onDispose { ServiceState.removeListener(listener) }
    }

    var enabled by remember { mutableStateOf(true) }
    var cnValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var enValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(service) {
        if (loaded || service == null) return@LaunchedEffect
        val prefs = ServiceState.prefs() ?: return@LaunchedEffect
        enabled = prefs.getBoolean(Config.KEY_ENABLED, true)
        cnValues = Config.decode(prefs.getString(Config.KEY_MAP_CN, null)).mapValues { (_, v) -> v }
        enValues = Config.decode(prefs.getString(Config.KEY_MAP_EN, null)).mapValues { (_, v) -> v }
        loaded = true
    }

    var cnMode by rememberSaveable { mutableStateOf(true) }
    var refsExpanded by remember { mutableStateOf(false) }

    val colors = CouiPageDefaults.themeColors()
    val accent = colors.accent
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveConfig(showToast: Boolean = true) {
        val prefs = ServiceState.prefs()
        if (prefs == null) {
            if (showToast) Toast.makeText(context, "保存失败：未连接 LSPosed 服务", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit()
            .putBoolean(Config.KEY_ENABLED, enabled)
            .putString(Config.KEY_MAP_CN, Config.encode(cnValues))
            .putString(Config.KEY_MAP_EN, Config.encode(enValues))
            .apply()
        if (showToast) {
            Toast.makeText(context, "已保存，输入法将自动重启生效", Toast.LENGTH_SHORT).show()
        }
        focusManager.clearFocus()
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = writeTextToUri(context, uri, Config.exportJson(enabled, cnValues, enValues))
                Toast.makeText(
                    context,
                    if (ok) "配置已导出" else "导出失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = importConfig(context, uri)
                if (data == null) {
                    Toast.makeText(context, "导入失败：无法读取或 JSON 格式不正确", Toast.LENGTH_SHORT).show()
                } else {
                    enabled = data.enabled
                    cnValues = data.cn
                    enValues = data.en
                    saveConfig(showToast = false)
                    Toast.makeText(context, "导入成功，输入法将自动重启生效", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "26键上滑字母", kind = CouiTextKind.ItemTitle) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Group(title = "模块状态") {
                if (service == null) {
                    Item {
                        Text(
                            text = "未连接 LSPosed 服务：请确认模块已在 LSPosed 中启用并作用于小布输入法，然后重启本应用。",
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                } else {
                    val scopeOk = remember(service) {
                        runCatching { service?.scope?.contains("com.oplus.keyboard") == true }.getOrDefault(false)
                    }
                    Item(
                        summary = {
                            Text(
                                text = if (scopeOk) {
                                    "${service?.frameworkName} ${service?.frameworkVersion} · 作用域正常"
                                } else {
                                    "${service?.frameworkName} ${service?.frameworkVersion} · 作用域未包含小布输入法"
                                },
                                kind = CouiTextKind.ItemSummary,
                                color = if (scopeOk) colors.summary else Color(0xFFEB3B2F),
                            )
                        },
                    ) {
                        Text(
                            text = "已连接 LSPosed",
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Group(title = "功能开关") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "启用上滑字母替换",
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            saveConfig(showToast = false)
                            Toast.makeText(context, "已保存，输入法将自动重启生效", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            MappingSection(
                cnMode = cnMode,
                onModeChange = { cnMode = it },
                cnValues = cnValues,
                enValues = enValues,
                accent = accent,
                cardColor = colors.card,
                titleColor = colors.title,
                hintColor = colors.summary,
                onCnChange = { letter, value -> cnValues = cnValues + (letter to value) },
                onEnChange = { letter, value -> enValues = enValues + (letter to value) },
                onRestoreCn = { cnValues = emptyMap() },
                onRestoreEn = { enValues = emptyMap() },
            )
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { saveConfig() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "保存并应用（输入法将自动重启）",
                    kind = CouiTextKind.ItemTitle,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(16.dp))

            Group(title = "数据") {
                Item(
                    onClick = {
                        exportConfigLauncher.launch("小布26键上滑配置.json")
                    },
                ) {
                    Text(
                        text = "导出配置到文件",
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
                Item(
                    onClick = {
                        importConfigLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                ) {
                    Text(
                        text = "从文件导入配置",
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Group(title = "我的仓库") {
                Item(
                    summary = {
                        Text(
                            text = "小布输入法 26 键上滑字母替换模块 · 点击打开仓库，欢迎 Star 与反馈问题",
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    },
                    onClick = { openUrl(PROJECT_URL) },
                ) {
                    Text(text = "项目仓库", kind = CouiTextKind.ItemTitle, color = colors.title)
                }
            }
            Spacer(Modifier.height(16.dp))

            Group(title = "引用与开源") {
                Item(
                    trailing = {
                        Text(
                            text = if (refsExpanded) "收起" else "展开",
                            kind = CouiTextKind.ItemSummary,
                            color = colors.accent,
                        )
                    },
                    onClick = { refsExpanded = !refsExpanded },
                ) {
                    Text(text = "引用库", kind = CouiTextKind.ItemTitle, color = colors.title)
                }
                if (refsExpanded) {
                    for (reference in REFERENCES) {
                        Item(
                            summary = {
                                Text(
                                    text = reference.url,
                                    kind = CouiTextKind.ItemSummary,
                                    color = colors.summary,
                                    maxLines = 1,
                                )
                            },
                            onClick = { openUrl(reference.url) },
                        ) {
                            Text(
                                text = reference.name,
                                kind = CouiTextKind.ItemTitle,
                                color = colors.title,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "留空表示禁用该键的上滑输入；点击「恢复默认」恢复当前中英文模式的默认符号。导出/导入为 JSON 文件，修改保存后输入法进程会重启生效。",
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MappingSection(
    cnMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    cnValues: Map<Char, String?>,
    enValues: Map<Char, String?>,
    accent: Color,
    cardColor: Color,
    titleColor: Color,
    hintColor: Color,
    onCnChange: (Char, String?) -> Unit,
    onEnChange: (Char, String?) -> Unit,
    onRestoreCn: () -> Unit,
    onRestoreEn: () -> Unit,
) {
    val values = if (cnMode) cnValues else enValues
    val defaults = if (cnMode) Config.CN_DEFAULTS else Config.EN_DEFAULTS
    val onValueChange = if (cnMode) onCnChange else onEnChange
    val onRestoreDefaults = if (cnMode) onRestoreCn else onRestoreEn

    Group(
        title = "26键上滑字母",
        headerActions = {
            Text(
                text = "恢复默认",
                kind = CouiTextKind.ItemSummary,
                color = accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRestoreDefaults,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                labels = listOf("中文26键", "英文26键"),
                selectedIndex = if (cnMode) 0 else 1,
                onSelected = { onModeChange(it == 0) },
            )
            Spacer(Modifier.height(12.dp))
            for (row in KEY_ROWS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (letter in row) {
                        val default = defaults.getOrNull(Config.LETTERS.indexOf(letter)) ?: ""
                        val current = values[letter]
                        KeyCell(
                            letter = letter,
                            text = current ?: "",
                            placeholder = default,
                            disabledHint = current == "",
                            accent = accent,
                            cardColor = cardColor,
                            onSurface = titleColor,
                            hintColor = hintColor,
                            onValueChange = { new -> onValueChange(letter, new) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun KeyCell(
    letter: Char,
    text: String,
    placeholder: String,
    disabledHint: Boolean,
    accent: Color,
    cardColor: Color,
    onSurface: Color,
    hintColor: Color,
    onValueChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = letter.uppercase(),
                kind = CouiTextKind.ItemSummary,
                color = hintColor,
            )
            BasicTextField(
                value = text,
                onValueChange = { new ->
                    if (new.length <= Config.MAX_SYMBOL_LENGTH) onValueChange(new)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = if (disabledHint) hintColor.copy(alpha = 0.45f) else onSurface,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                kind = CouiTextKind.ItemSummary,
                                color = hintColor.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/* ---------------- 文件读写 ---------------- */

private suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: return@withContext false
            true
        } catch (t: Throwable) {
            false
        }
    }

private suspend fun importConfig(context: Context, uri: Uri): Config.ImportData? {
    val raw = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            }
        } catch (t: Throwable) {
            null
        }
    }
    return raw?.let { Config.parseImport(it) }
}
