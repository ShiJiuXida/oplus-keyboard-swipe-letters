package com.kbswipe.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Scaffold
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import io.github.libxposed.service.XposedService

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

    var exportText by remember { mutableStateOf<String?>(null) }
    var importOpen by remember { mutableStateOf(false) }

    val colors = CouiPageDefaults.themeColors()
    val accent = colors.accent
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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

            MappingGroup(
                groupTitle = "中文 26 键（拼音 / 五笔）",
                values = cnValues,
                defaults = Config.CN_DEFAULTS,
                accent = accent,
                cardColor = colors.card,
                titleColor = colors.title,
                hintColor = colors.summary,
                onValueChange = { letter, value -> cnValues = cnValues + (letter to value) },
                onRestoreDefaults = { cnValues = emptyMap() },
            )
            Spacer(Modifier.height(16.dp))

            MappingGroup(
                groupTitle = "英文 26 键",
                values = enValues,
                defaults = Config.EN_DEFAULTS,
                accent = accent,
                cardColor = colors.card,
                titleColor = colors.title,
                hintColor = colors.summary,
                onValueChange = { letter, value -> enValues = enValues + (letter to value) },
                onRestoreDefaults = { enValues = emptyMap() },
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
                        exportText = Config.exportJson(enabled, cnValues, enValues)
                    },
                ) {
                    Text(
                        text = "导出配置",
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
                Item(
                    onClick = { importOpen = true },
                ) {
                    Text(
                        text = "导入配置",
                        kind = CouiTextKind.ItemTitle,
                        color = colors.title,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "留空表示禁用该键的上滑输入；点击「恢复默认」恢复全部默认符号。修改保存后输入法进程会重启生效。",
                kind = CouiTextKind.ItemSummary,
                color = colors.summary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    exportText?.let { json ->
        ExportDialog(
            json = json,
            colors = colors,
            onDismiss = { exportText = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("小布26键上滑配置", json))
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (importOpen) {
        ImportDialog(
            colors = colors,
            onDismiss = { importOpen = false },
            onApply = { raw ->
                val data = Config.parseImport(raw)
                if (data == null) {
                    Toast.makeText(context, "导入失败：JSON 格式不正确", Toast.LENGTH_SHORT).show()
                } else {
                    enabled = data.enabled
                    cnValues = data.cn
                    enValues = data.en
                    importOpen = false
                    saveConfig()
                }
            },
        )
    }
}

@Composable
private fun MappingGroup(
    groupTitle: String,
    values: Map<Char, String?>,
    defaults: List<String>,
    accent: Color,
    cardColor: Color,
    titleColor: Color,
    hintColor: Color,
    onValueChange: (Char, String?) -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    Group(
        title = groupTitle,
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

@Composable
private fun ExportDialog(
    json: String,
    colors: com.itos.coui.layout.CouiPageColors,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(20.dp),
        ) {
            Text(text = "导出配置", kind = CouiTextKind.ItemTitle, color = colors.title)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = json,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.title,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction(text = "取消", color = colors.summary, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogAction(text = "复制", color = colors.accent, onClick = onCopy)
            }
        }
    }
}

@Composable
private fun ImportDialog(
    colors: com.itos.coui.layout.CouiPageColors,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(20.dp),
        ) {
            Text(text = "导入配置", kind = CouiTextKind.ItemTitle, color = colors.title)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    color = colors.title,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text = "在此粘贴 JSON 配置文本",
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary.copy(alpha = 0.6f),
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction(text = "取消", color = colors.summary, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                DialogAction(text = "应用", color = colors.accent, onClick = { onApply(text) })
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .padding(horizontal = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, kind = CouiTextKind.ItemTitle, color = color)
    }
}
