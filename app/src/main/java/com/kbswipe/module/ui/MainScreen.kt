package com.kbswipe.module.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar
import com.kbswipe.module.Config
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch

/** 版本 B：玻璃质感风格（背景模糊采样 + 液态 ChipTab + 页面切换动效）。 */
@Composable
internal fun MainScreen() {
    var service by remember { mutableStateOf<XposedService?>(null) }
    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = { service = it }
        ServiceState.addListener(listener, notifyImmediately = true)
        onDispose { ServiceState.removeListener(listener) }
    }

    var enabled by rememberSaveable { mutableStateOf(true) }
    var swapMain by rememberSaveable { mutableStateOf(Config.DEFAULT_SWAP_MAIN) }
    var swapPanel by rememberSaveable { mutableStateOf(Config.DEFAULT_SWAP_PANEL) }
    var hideClipSetting by rememberSaveable { mutableStateOf(Config.DEFAULT_HIDE_CLIP_SETTING) }
    var cnValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var enValues by remember { mutableStateOf<Map<Char, String?>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var tabKey by rememberSaveable { mutableStateOf("cn") }
    var editingKey by remember { mutableStateOf<Char?>(null) }
    var aboutOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(service) {
        if (loaded || service == null) return@LaunchedEffect
        val prefs = ServiceState.prefs() ?: return@LaunchedEffect
        enabled = prefs.getBoolean(Config.KEY_ENABLED, true)
        swapMain = prefs.getBoolean(Config.KEY_SWAP_MAIN, Config.DEFAULT_SWAP_MAIN)
        swapPanel = prefs.getBoolean(Config.KEY_SWAP_PANEL, Config.DEFAULT_SWAP_PANEL)
        hideClipSetting = prefs.getBoolean(Config.KEY_HIDE_CLIP_SETTING, Config.DEFAULT_HIDE_CLIP_SETTING)
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
            .putBoolean(Config.KEY_SWAP_MAIN, swapMain)
            .putBoolean(Config.KEY_SWAP_PANEL, swapPanel)
            .putBoolean(Config.KEY_HIDE_CLIP_SETTING, hideClipSetting)
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

    val backdrop = rememberCouiBackdrop(diagnosticName = "kbswipe")
    val dark = CouiTheme.isDark

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景采样源：亮色灰白、深色纯深灰黑（参考 COUI 示例的深色处理），玻璃组件从中采样模糊。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .couiBackdrop(backdrop)
                .background(
                    if (dark) Color(0xFF101215) else Color(0xFFEFF2F6),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            TopAppBar(
                backdrop = backdrop,
                title = { Text(text = "小布键盘助手", kind = CouiTextKind.ItemTitle) },
                actions = {
                    Text(
                        text = "关于",
                        kind = CouiTextKind.ItemTitle,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { aboutOpen = true },
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                },
            )

            ChipTabRow(
                backdrop = backdrop,
                items = listOf(
                    CouiChipTabItem(key = "cn", label = "中文26键"),
                    CouiChipTabItem(key = "en", label = "英文26键"),
                    CouiChipTabItem(key = "more", label = "模块与数据"),
                ),
                selectedKey = tabKey,
                onSelectedKeyChange = { tabKey = it as String },
                animationLevel = 1,
                contourGlowEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )

            AnimatedContent(
                targetState = tabKey,
                transitionSpec = {
                    val duration = 180
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration))) togetherWith
                            (slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                    } else {
                        (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration))) togetherWith
                            (slideOutHorizontally(tween(duration)) { it / 4 } + fadeOut(tween(duration)))
                    }
                },
                label = "page-switch",
            ) { key ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    when (key) {
                        "cn" -> KeyboardTabContent(
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

                        "en" -> KeyboardTabContent(
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

                        else -> MoreTabContent(
                            service = service,
                            enabled = enabled,
                            onEnabledChange = { value ->
                                enabled = value
                                saveConfig(showToast = false)
                                toast("已保存，输入法将自动重启生效")
                            },
                            swapMain = swapMain,
                            onSwapMainChange = { value ->
                                swapMain = value
                                saveConfig(showToast = false)
                                toast("已保存，输入法将自动重启生效")
                            },
                            swapPanel = swapPanel,
                            onSwapPanelChange = { value ->
                                swapPanel = value
                                saveConfig(showToast = false)
                                toast("已保存，输入法将自动重启生效")
                            },
                            hideClipSetting = hideClipSetting,
                            onHideClipSettingChange = { value ->
                                hideClipSetting = value
                                saveConfig(showToast = false)
                                toast("已保存，输入法将自动重启生效")
                            },
                            onExport = { exportConfigLauncher.launch("小布26键上滑配置.json") },
                            onImport = {
                                importConfigLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                                )
                            },
                            accent = accent,
                            colors = colors,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { saveConfig() },
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
    }

    editingKey?.let { letter ->
        val isCn = tabKey == "cn"
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
            colors = colors,
            onOpenUrl = ::openUrl,
            onDismiss = { aboutOpen = false },
        )
    }
}

@Composable
private fun KeyboardTabContent(
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
        KeyboardGrid(
            values = values,
            defaults = defaults,
            accent = accent,
            colors = colors,
            onKeyClick = onKeyClick,
        )
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
private fun MoreTabContent(
    service: XposedService?,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    swapMain: Boolean,
    onSwapMainChange: (Boolean) -> Unit,
    swapPanel: Boolean,
    onSwapPanelChange: (Boolean) -> Unit,
    hideClipSetting: Boolean,
    onHideClipSettingChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.divider),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "左下角换位 · 主键盘",
                    style = TextStyle(color = colors.title, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = swapMain, onCheckedChange = onSwapMainChange)
            }
            BasicText(
                text = "主键盘左下角 符号 ⇄ 123 换位",
                style = TextStyle(color = colors.summary, fontSize = 11.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.divider),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "左下角换位 · 符号/数字面板",
                    style = TextStyle(color = colors.title, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = swapPanel, onCheckedChange = onSwapPanelChange)
            }
            BasicText(
                text = "点进符号面板 返回 ⇄ 123；点进 123 面板 符号 ⇄ 返回",
                style = TextStyle(color = colors.summary, fontSize = 11.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(colors.divider),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = "隐藏剪切板设置键",
                    style = TextStyle(color = colors.title, fontSize = 15.sp),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = hideClipSetting, onCheckedChange = onHideClipSettingChange)
            }
            BasicText(
                text = "剪切板 / 常用语顶栏最右侧的设置齿轮不显示，编辑键保留",
                style = TextStyle(color = colors.summary, fontSize = 11.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
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
}
