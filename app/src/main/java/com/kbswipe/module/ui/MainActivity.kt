package com.kbswipe.module.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.itos.coui.theme.CouiTheme
import com.kbswipe.module.Config
import com.kbswipe.module.R
import kotlinx.coroutines.Dispatchers
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

internal const val VERSION_LABEL = "1.9"
internal const val TARGET_PACKAGE = "com.oplus.keyboard"
internal const val PROJECT_URL = "https://github.com/ShiJiuXida/oplus-keyboard-swipe-letters"

internal val KEY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
internal val KEY_ROW_INDENTS = listOf(0f, 0.45f, 1.35f)

internal val DANGER = Color(0xFFEB3B2F)

/** 引用过的开源库（二级菜单项）。 */
internal data class ReferenceItem(val name: String, val url: String)

internal val REFERENCES = listOf(
    ReferenceItem("COUI-Kit（界面框架）", "https://github.com/ItosEO/COUI-Kit"),
    ReferenceItem("libxposed/api（模块 API）", "https://github.com/libxposed/api"),
    ReferenceItem("libxposed/service（框架服务）", "https://github.com/libxposed/service"),
)

/* ---------------- 共享组件 ---------------- */

@Composable
internal fun SettingsCard(
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
            .shadow(3.dp, RoundedCornerShape(16.dp))
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
internal fun SettingRow(
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

@Composable
internal fun SoftButton(
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
internal fun KeyCap(
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
    val dark = CouiTheme.isDark
    val (bg, fg, border) = when (status) {
        1 -> Triple(DANGER.copy(alpha = 0.14f), DANGER, DANGER.copy(alpha = 0.55f))
        2 -> Triple(accent.copy(alpha = 0.14f), accent, accent.copy(alpha = 0.55f))
        else -> Triple(
            colors.background,
            colors.title,
            if (dark) Color.White.copy(alpha = 0.22f) else colors.title.copy(alpha = 0.18f),
        )
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

@Composable
internal fun KeyboardGrid(
    values: Map<Char, String?>,
    defaults: List<String>,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onKeyClick: (Char) -> Unit,
) {
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
                    KeyCap(
                        letter = letter,
                        text = values[letter],
                        placeholder = default,
                        accent = accent,
                        colors = colors,
                        onOpen = { onKeyClick(letter) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 3.dp),
                    )
                }
                if (indent > 0f) Spacer(Modifier.weight(indent))
            }
        }
    }
}

/* ---------------- 对话框 ---------------- */

@Composable
internal fun EditKeyDialog(
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
    // 深色主题下 card 是玻璃半透明色，对话框窗口后无采样层，改用实心深灰保证可读性。
    val dialogBg = if (CouiTheme.isDark) Color(0xFF1E2025) else colors.card

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBg)
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
internal fun ConfirmDialog(
    title: String,
    message: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogBg = if (CouiTheme.isDark) Color(0xFF1E2025) else colors.card

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBg)
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
internal fun AboutLinkRow(
    title: String,
    summary: String,
    titleColor: Color = Color.Unspecified,
    colors: com.itos.coui.layout.CouiPageColors,
    onClick: () -> Unit,
) {
    val resolvedTitleColor = if (titleColor == Color.Unspecified) colors.accent else titleColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = resolvedTitleColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = summary,
                style = TextStyle(color = colors.summary, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        BasicText(
            text = "›",
            style = TextStyle(color = colors.summary, fontSize = 20.sp),
        )
    }
}

@Composable
internal fun AboutDialog(
    colors: com.itos.coui.layout.CouiPageColors,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogBg = if (CouiTheme.isDark) Color(0xFF1E2025) else colors.card

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "‹",
                        style = TextStyle(color = colors.title, fontSize = 30.sp),
                    )
                }
                BasicText(
                    text = "关于",
                    style = TextStyle(
                        color = colors.title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                SettingsCard(
                    title = "关于本模块",
                    subtitle = "小布键盘助手 · LSPosed 模块",
                    colors = colors,
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
                    ) {
                        BasicText(
                            text = "本模块完全免费，禁止任何形式的倒卖。",
                            style = TextStyle(
                                color = colors.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        BasicText(
                            text = "功能：26 键上滑字母自定义；左下角功能键换位（主键盘 符号 ⇄ 123，符号/数字面板 返回/符号 ⇄ 123）。",
                            style = TextStyle(color = colors.summary, fontSize = 13.sp),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                SettingsCard(title = "免责声明", subtitle = null, colors = colors) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                    ) {
                        BasicText(
                            text = "本模块仅供学习与研究使用，请勿用于商业用途。",
                            style = TextStyle(color = colors.summary, fontSize = 13.sp),
                        )
                        BasicText(
                            text = "修改输入法行为可能产生意外结果，请自行备份配置并承担使用风险。",
                            style = TextStyle(color = colors.summary, fontSize = 13.sp),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                SettingsCard(title = "赞赏支持", subtitle = null, colors = colors) {
                    Column {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.donate_v),
                                contentDescription = "赞赏码",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                            )
                            Spacer(Modifier.width(12.dp))
                            Image(
                                painter = painterResource(R.drawable.donate_z),
                                contentDescription = "赞赏码",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                            )
                        }
                        BasicText(
                            text = "感谢每一份支持，这是模块持续维护的最大动力。",
                            style = TextStyle(
                                color = colors.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
                        )
                    }
                }

                SettingsCard(
                    title = "联系与反馈",
                    subtitle = "点击条目直接跳转",
                    colors = colors,
                ) {
                    Column {
                        AboutLinkRow(
                            title = "我的酷安主页",
                            summary = "coolapk.com/u/3138531",
                            titleColor = colors.title,
                            colors = colors,
                            onClick = { onOpenUrl("https://www.coolapk.com/u/3138531") },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.divider),
                        )
                        AboutLinkRow(
                            title = "项目仓库",
                            summary = "github.com/ShiJiuXida/oplus-keyboard-swipe-letters",
                            titleColor = colors.title,
                            colors = colors,
                            onClick = { onOpenUrl(PROJECT_URL) },
                        )
                    }
                }

                SettingsCard(title = "引用与开源", subtitle = null, colors = colors) {
                    Column {
                        for (reference in REFERENCES) {
                            AboutLinkRow(
                                title = reference.name,
                                summary = reference.url,
                                titleColor = colors.title,
                                colors = colors,
                                onClick = { onOpenUrl(reference.url) },
                            )
                        }
                    }
                }

                BasicText(
                    text = "v$VERSION_LABEL · 感谢使用",
                    style = TextStyle(
                        color = colors.summary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 18.dp),
                )
            }
        }
    }
}

/* ---------------- 文件读写 ---------------- */

internal suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean =
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

internal suspend fun importConfig(context: Context, uri: Uri): Config.ImportData? {
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
