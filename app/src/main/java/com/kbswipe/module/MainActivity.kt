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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.switch.Switch
import com.itos.coui.theme.CouiTheme
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

private const val VERSION_LABEL = "1.4"
private const val TARGET_PACKAGE = "com.oplus.keyboard"
private const val PROJECT_URL = "https://github.com/ShiJiuXida/oplus-keyboard-swipe-letters"

private val KEY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val KEY_ROW_INDENTS = listOf(0f, 0.45f, 1.35f)

private val DANGER = Color(0xFFEB3B2F)
private val TAB_TRACK = Color(0xFFEDF1F7)

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

    var enabled by rememberSaveable { mutableStateOf(true) }
    var cnValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var enValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var editingKey by remember { mutableStateOf<Char?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf<Boolean?>(null) }
    var refsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(service) {
        if (loaded || service == null) return@LaunchedEffect
        val prefs = ServiceState.prefs() ?: return@LaunchedEffect
        enabled = prefs.getBoolean(Config.KEY_ENABLED, true)
        cnValues = Config.decode(prefs.getString(Config.KEY_MAP_CN, null)).mapValues { (_, v) -> v }
        enValues = Config.decode(prefs.getString(Config.KEY_MAP_EN, null)).mapValues { (_, v) -> v }
        loaded = true
    }

    val colors = CouiPageDefaults.themeColors()
    val accent = colors.accent
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            toast("无法打开链接")
        }
    }

    fun saveConfig(showToast: Boolean = true) {
        val prefs = ServiceState.prefs()
        if (prefs == null) {
            if (showToast) toast("保存失败：未连接 LSPosed 服务")
            return
        }
        prefs.edit()
            .putBoolean(Config.KEY_ENABLED, enabled)
            .putString(Config.KEY_MAP_CN, Config.encode(cnValues))
            .putString(Config.KEY_MAP_EN, Config.encode(enValues))
            .apply()
        if (showToast) toast("已保存，输入法将自动重启生效")
        focusManager.clearFocus()
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = writeTextToUri(context, uri, Config.exportJson(enabled, cnValues, enValues))
                toast(if (ok) "配置已导出" else "导出失败")
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
                    toast("导入失败：无法读取或 JSON 格式不正确")
                } else {
                    enabled = data.enabled
                    cnValues = data.cn
                    enValues = data.en
                    saveConfig(showToast = false)
                    toast("导入成功，输入法将自动重启生效")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(
            title = "26键上滑字母",
            subtitle = "v$VERSION_LABEL · 修改后点击底部保存",
            accent = accent,
            colors = colors,
            onAbout = { aboutOpen = true },
        )

        TabBar(
            tabs = listOf("中文26键", "英文26键", "模块与数据"),
            selectedIndex = tabIndex,
            accent = accent,
            colors = colors,
            onSelect = { tabIndex = it },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            when (tabIndex) {
                0 -> KeyboardCard(
                    title = "中文 26 键",
                    subtitle = "点击按键设置该键上滑符号；「禁用」后该键不再响应上滑。",
                    values = cnValues,
                    defaults = Config.CN_DEFAULTS,
                    accent = accent,
                    colors = colors,
                    onKeyClick = { editingKey = it },
                    onRestore = { cnValues = emptyMap() },
                    onClear = { clearConfirm = true },
                )

                1 -> KeyboardCard(
                    title = "英文 26 键",
                    subtitle = "点击按键设置该键上滑符号；「禁用」后该键不再响应上滑。",
                    values = enValues,
                    defaults = Config.EN_DEFAULTS,
                    accent = accent,
                    colors = colors,
                    onKeyClick = { editingKey = it },
                    onRestore = { enValues = emptyMap() },
                    onClear = { clearConfirm = false },
                )

                else -> SettingsTab(
                    service = service,
                    enabled = enabled,
                    onEnabledChange = { value ->
                        enabled = value
                        saveConfig(showToast = false)
                        toast("已保存，输入法将自动重启生效")
                    },
                    refsExpanded = refsExpanded,
                    onRefsToggle = { refsExpanded = !refsExpanded },
                    onExport = { exportConfigLauncher.launch("小布26键上滑配置.json") },
                    onImport = {
                        importConfigLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                    onOpenUrl = ::openUrl,
                    accent = accent,
                    colors = colors,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        SaveBar(
            accent = accent,
            colors = colors,
            onSave = { saveConfig() },
        )
    }

    editingKey?.let { letter ->
        val isCn = tabIndex == 0
        val values = if (isCn) cnValues else enValues
        val defaults = if (isCn) Config.CN_DEFAULTS else Config.EN_DEFAULTS
        EditKeyDialog(
            letter = letter,
            current = values[letter],
            default = defaults.getOrNull(Config.LETTERS.indexOf(letter)) ?: "",
            accent = accent,
            colors = colors,
            onApply = { value ->
                if (isCn) cnValues = cnValues + (letter to value)
                else enValues = enValues + (letter to value)
                editingKey = null
            },
            onDismiss = { editingKey = null },
        )
    }

    clearConfirm?.let { isCn ->
        ConfirmDialog(
            title = "清空全部按键？",
            message = "将把该模式下所有键设为禁用，保存前仍可修改。",
            accent = accent,
            colors = colors,
            onConfirm = {
                val cleared = KEY_ROWS.joinToString("").associate { it to "" }
                if (isCn) cnValues = cleared else enValues = cleared
                clearConfirm = null
            },
            onDismiss = { clearConfirm = null },
        )
    }

    if (aboutOpen) {
        AboutDialog(
            accent = accent,
            colors = colors,
            onOpenUrl = ::openUrl,
            onDismiss = { aboutOpen = false },
        )
    }
}

/* ---------------- 头部与 Tab ---------------- */

@Composable
private fun Header(
    title: String,
    subtitle: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onAbout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = "小布输入法 · LSPosed",
                style = TextStyle(
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.padding(top = 3.dp),
            )
            BasicText(
                text = subtitle,
                style = TextStyle(color = colors.summary, fontSize = 12.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(accent.copy(alpha = 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAbout,
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "关于",
                style = TextStyle(color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun TabBar(
    tabs: List<String>,
    selectedIndex: Int,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(TAB_TRACK)
                .padding(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, title ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .then(
                                if (selected) {
                                    Modifier
                                        .shadow(2.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.card)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = title,
                            style = TextStyle(
                                color = if (selected) accent else colors.summary,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- 卡片与按键 ---------------- */

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    colors: com.itos.coui.layout.CouiPageColors,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 11.dp)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (!subtitle.isNullOrEmpty()) {
                BasicText(
                    text = subtitle,
                    style = TextStyle(color = colors.summary, fontSize = 12.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        content()
    }
}

@Composable
private fun KeyboardCard(
    title: String,
    subtitle: String,
    values: Map<Char, String?>,
    defaults: List<String>,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onKeyClick: (Char) -> Unit,
    onRestore: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsCard(title = title, subtitle = subtitle, colors = colors) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp)) {
            KEY_ROWS.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val indent = KEY_ROW_INDENTS[rowIndex]
                    if (indent > 0f) Spacer(Modifier.weight(indent))
                    for (letter in row) {
                        val default = defaults.getOrNull(Config.LETTERS.indexOf(letter)) ?: ""
                        val current = values[letter]
                        KeyCap(
                            letter = letter,
                            text = current,
                            placeholder = default,
                            accent = accent,
                            colors = colors,
                            onOpen = { onKeyClick(letter) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (indent > 0f) Spacer(Modifier.weight(indent))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        ) {
            SoftButton(
                text = "恢复默认",
                background = accent.copy(alpha = 0.1f),
                contentColor = accent,
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            SoftButton(
                text = "清空全部",
                background = DANGER.copy(alpha = 0.1f),
                contentColor = DANGER,
                onClick = onClear,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SoftButton(
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun KeyCap(
    letter: Char,
    text: String?,
    placeholder: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when {
        text == null -> 0
        text.isEmpty() -> 1
        else -> 2
    }
    val (bg, fg, border) = when (status) {
        1 -> Triple(DANGER.copy(alpha = 0.1f), DANGER, DANGER.copy(alpha = 0.4f))
        2 -> Triple(accent.copy(alpha = 0.1f), accent, accent.copy(alpha = 0.4f))
        else -> Triple(colors.background, colors.title, colors.divider)
    }
    val label = when (status) {
        1 -> "禁用"
        2 -> text ?: ""
        else -> placeholder
    }
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                text = letter.uppercase(),
                style = TextStyle(
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            BasicText(
                text = label,
                style = TextStyle(
                    color = if (status == 0) colors.summary else fg,
                    fontSize = 8.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/* ---------------- 设置页 ---------------- */

@Composable
private fun SettingsTab(
    service: XposedService?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    refsExpanded: Boolean,
    onRefsToggle: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenUrl: (String) -> Unit,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
) {
    val scopeOk = remember(service) {
        runCatching { service?.scope?.contains(TARGET_PACKAGE) == true }.getOrDefault(false)
    }
    val activated = service != null && scopeOk

    SettingsCard(title = "模块状态", subtitle = null, colors = colors) {
        Column {
            SettingRow(
                title = "模块状态",
                summary = if (activated) "已激活 · 作用域包含小布输入法" else "未激活 · 请在 LSPosed 中启用并勾选小布输入法",
                summaryColor = if (activated) colors.summary else DANGER,
                colors = colors,
            )
            service?.let { s ->
                SettingRow(
                    title = s.frameworkName,
                    summary = "${s.frameworkVersion} · 作用域：$TARGET_PACKAGE",
                    colors = colors,
                )
            }
        }
    }

    SettingsCard(title = "功能开关", subtitle = null, colors = colors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "启用上滑字母替换",
                style = TextStyle(color = colors.title, fontSize = 15.sp),
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }

    SettingsCard(title = "数据管理", subtitle = null, colors = colors) {
        Column {
            SettingRow(
                title = "导出配置到文件",
                summary = "保存为 JSON 文件",
                colors = colors,
                onClick = onExport,
            )
            SettingRow(
                title = "从文件导入配置",
                summary = "读取 JSON 文件并应用",
                colors = colors,
                onClick = onImport,
            )
        }
    }

    SettingsCard(title = "我的仓库", subtitle = null, colors = colors) {
        Column {
            SettingRow(
                title = "项目仓库",
                summary = "小布输入法 26 键上滑字母替换模块 · 点击打开仓库，欢迎 Star 与反馈问题",
                colors = colors,
                onClick = { onOpenUrl(PROJECT_URL) },
            )
        }
    }

    SettingsCard(title = "引用与开源", subtitle = null, colors = colors) {
        Column {
            SettingRow(
                title = "引用库",
                summary = if (refsExpanded) "点击收起" else "点击展开",
                summaryColor = accent,
                colors = colors,
                onClick = onRefsToggle,
            )
            if (refsExpanded) {
                for (reference in REFERENCES) {
                    SettingRow(
                        title = reference.name,
                        summary = reference.url,
                        colors = colors,
                        onClick = { onOpenUrl(reference.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    summaryColor: Color = Color.Unspecified,
    colors: com.itos.coui.layout.CouiPageColors,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = colors.title, fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrEmpty()) {
                BasicText(
                    text = summary,
                    style = TextStyle(
                        color = if (summaryColor == Color.Unspecified) colors.summary else summaryColor,
                        fontSize = 12.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/* ---------------- 保存栏 ---------------- */

@Composable
private fun SaveBar(
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp)
            .background(colors.card)
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 11.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSave,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "保存并应用配置",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/* ---------------- 对话框 ---------------- */

@Composable
private fun EditKeyDialog(
    letter: Char,
    current: String?,
    default: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onApply: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current ?: "") }
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(20.dp),
        ) {
            BasicText(
                text = "${letter.uppercase()} 键 · 上滑符号",
                style = TextStyle(
                    color = colors.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = "默认符号：$default。输入文字自定义，留空恢复默认。",
                style = TextStyle(color = colors.summary, fontSize = 12.sp),
                modifier = Modifier.padding(top = 6.dp),
            )
            BasicTextField(
                value = draft,
                onValueChange = { new ->
                    if (new.length <= Config.MAX_SYMBOL_LENGTH) draft = new
                },
                singleLine = true,
                textStyle = TextStyle(color = colors.title, fontSize = 15.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.isEmpty()) {
                            BasicText(
                                text = "恢复默认",
                                style = TextStyle(color = colors.summary.copy(alpha = 0.6f), fontSize = 15.sp),
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(
                    text = "禁用",
                    background = DANGER.copy(alpha = 0.1f),
                    contentColor = DANGER,
                    onClick = { onApply("") },
                    modifier = Modifier.weight(1f),
                )
                SoftButton(
                    text = "取消",
                    background = colors.background,
                    contentColor = colors.summary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SoftButton(
                    text = "确定",
                    background = accent,
                    contentColor = Color.White,
                    onClick = { onApply(draft.takeIf { it.isNotEmpty() }) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(20.dp),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = colors.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = message,
                style = TextStyle(color = colors.summary, fontSize = 13.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton(
                    text = "取消",
                    background = colors.background,
                    contentColor = colors.summary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SoftButton(
                    text = "清空",
                    background = DANGER.copy(alpha = 0.1f),
                    contentColor = DANGER,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(20.dp),
        ) {
            BasicText(
                text = "小布26键上滑 v$VERSION_LABEL",
                style = TextStyle(
                    color = colors.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = "自定义小布输入法中英文 26 键键盘的上滑字母 / 符号。",
                style = TextStyle(color = colors.summary, fontSize = 12.sp),
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            SettingRow(
                title = "项目仓库",
                summary = PROJECT_URL,
                summaryColor = accent,
                colors = colors,
                onClick = { onOpenUrl(PROJECT_URL) },
            )
            for (reference in REFERENCES) {
                SettingRow(
                    title = reference.name,
                    summary = reference.url,
                    summaryColor = accent,
                    colors = colors,
                    onClick = { onOpenUrl(reference.url) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "关闭",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
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
