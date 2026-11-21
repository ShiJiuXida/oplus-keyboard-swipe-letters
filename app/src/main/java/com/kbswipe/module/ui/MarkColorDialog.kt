package com.kbswipe.module.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kbswipe.module.Config

/** 常用角标色板：深浅色主题下都够用的高对比色。 */
private val MARK_COLOR_PRESETS = listOf(
    0xFFE8451C.toInt(), // 橙红
    0xFFFF6A00.toInt(), // 橙
    0xFFFFB300.toInt(), // 琥珀
    0xFF43A047.toInt(), // 绿
    0xFF00897B.toInt(), // 青
    0xFF1E88E5.toInt(), // 蓝
    0xFF3949AB.toInt(), // 靛
    0xFF8E24AA.toInt(), // 紫
    0xFFD81B60.toInt(), // 玫红
    0xFF6D4C41.toInt(), // 棕
    0xFF546E7A.toInt(), // 蓝灰
    0xFF000000.toInt(), // 纯黑
    0xFF444444.toInt(), // 深灰
    0x88000000.toInt(), // 半透明黑（原生角标观感）
    0xFFFFFFFF.toInt(), // 纯白
    0xFF00E5FF.toInt(), // 亮青
)

/**
 * 上滑字符取色对话框：预设色板 + HEX 输入。
 * 不引入额外依赖，只用 foundation 的 BasicTextField / Box。
 */
@Composable
internal fun MarkColorDialog(
    current: Int,
    title: String,
    accent: Color,
    colors: com.itos.coui.layout.CouiPageColors,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(current) }
    // 只编辑 RGB；alpha 在应用时统一按不透明写入（角标必须清晰可见）
    var hexInput by remember { mutableStateOf(String.format("%06X", current and 0xFFFFFF)) }
    var invalid by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                .padding(18.dp),
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
                text = "修改键帽右上角上滑符号的颜色；白天 / 黑夜两套自动跟随主题切换。",
                style = TextStyle(color = colors.summary, fontSize = 11.sp),
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(picked))
                        .border(1.dp, colors.divider, RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    BasicText(
                        text = "预览",
                        style = TextStyle(color = colors.title, fontSize = 13.sp),
                    )
                    BasicText(
                        text = Config.colorToHex(picked),
                        style = TextStyle(color = colors.summary, fontSize = 11.sp),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 14.dp)) {
                MARK_COLOR_PRESETS.chunked(8).forEach { rowColors ->
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowColors.forEach { value ->
                            val selected = value == picked
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(value))
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) accent else colors.divider,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            picked = value
                                            hexInput = String.format("%06X", value and 0xFFFFFF)
                                            invalid = false
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "#",
                    style = TextStyle(color = colors.summary, fontSize = 14.sp),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(
                            1.dp,
                            if (invalid) DANGER else colors.divider,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            val filtered = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                            hexInput = filtered
                            val parsed = filtered.toLongOrNull(16)
                            invalid = filtered.length != 6 || parsed == null
                            if (!invalid && parsed != null) {
                                picked = (0xFF000000L or parsed).toInt()
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.title, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                    )
                }
            }
            if (invalid) {
                BasicText(
                    text = "请输入 6 位十六进制颜色，例如 E8451C",
                    style = TextStyle(color = DANGER, fontSize = 11.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(modifier = Modifier.padding(top = 16.dp)) {
                SoftButton(
                    text = "取消",
                    background = colors.background,
                    contentColor = colors.title,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SoftButton(
                    text = "应用",
                    background = if (invalid) accent.copy(alpha = 0.4f) else accent,
                    contentColor = Color.White,
                    onClick = { if (!invalid) onApply(picked) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
