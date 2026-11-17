package com.kbswipe.module.hook

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 隐藏剪切板 / 常用语页顶栏最右侧的设置齿轮（iv_clip_setting），保留编辑键。
 *
 * 顶栏控件由同一个 ConstraintLayout Group（group_bar）管理可见性：
 * Group 每次布局都会把自身可见性重新写给被引用控件，所以只设 GONE 会被下一帧还原，
 * 需要先把设置键的 id 从 Group 的引用列表里摘掉；编辑键继续留在列表里，保持原有显隐行为。
 *
 * id 全部按资源名解析，不依赖混淆类名，也不依赖 id 数值。
 */
object ClipboardPatcher {

    private const val TAG = "SwipeKeys"
    private const val PKG = "com.oplus.keyboard"
    private const val LAYOUT_NAME = "lib_input_clip_board_view"
    private const val GROUP_BAR = "group_bar"
    private const val IV_BACK = "iv_clip_back"
    private const val IV_TABS = "sw_clip_selected"
    private const val IV_FUNC = "iv_clip_func"
    private const val IV_SETTING = "iv_clip_setting"

    /** 0=未解析，-1=宿主没有该资源，正数=layout id。 */
    private val layoutIdCache = AtomicInteger(0)
    private val hitLogged = AtomicBoolean(false)

    fun install(module: XposedInterface): Boolean {
        val inflate = try {
            LayoutInflater::class.java.getDeclaredMethod(
                "inflate",
                Int::class.javaPrimitiveType,
                ViewGroup::class.java,
                Boolean::class.javaPrimitiveType,
            )
        } catch (_: Throwable) {
            return false
        }
        // inflate 是热点方法，先反优化，避免调用点持有内联副本
        try {
            module.deoptimize(inflate)
        } catch (_: Throwable) {
        }
        module.hook(inflate).intercept { chain ->
            val result = chain.proceed()
            try {
                val inflater = chain.thisObject as? LayoutInflater
                val layoutId = chain.args.firstOrNull() as? Int
                if (inflater != null && layoutId != null && layoutId == clipBoardLayoutId(inflater)) {
                    val view = result as? View
                    if (view != null && hideSettingIcon(view) && hitLogged.compareAndSet(false, true)) {
                        module.log(Log.INFO, TAG, "event=hit_clip_hide target=$IV_SETTING")
                    }
                }
            } catch (_: Throwable) {
                // 布局结构变化不应影响宿主，静默跳过
            }
            result
        }
        return true
    }

    private fun clipBoardLayoutId(inflater: LayoutInflater): Int {
        val cached = layoutIdCache.get()
        if (cached != 0) return if (cached > 0) cached else 0
        val id = try {
            inflater.context.resources.getIdentifier(LAYOUT_NAME, "layout", PKG)
        } catch (_: Throwable) {
            0
        }
        if (id != 0) {
            layoutIdCache.compareAndSet(0, id)
        } else {
            layoutIdCache.compareAndSet(0, -1)
        }
        return id
    }

    private fun hideSettingIcon(root: View): Boolean {
        val resources = root.resources
        fun idOf(name: String): Int = resources.getIdentifier(name, "id", PKG)

        val funcId = idOf(IV_FUNC)
        val settingId = idOf(IV_SETTING)
        if (funcId == 0 || settingId == 0) return false
        val setting = root.findViewById<View>(settingId) ?: return false

        val groupId = idOf(GROUP_BAR)
        val group = if (groupId != 0) root.findViewById<View>(groupId) else null
        if (group != null) {
            val keep = intArrayOf(idOf(IV_BACK), idOf(IV_TABS), funcId).filter { it != 0 }.toIntArray()
            if (keep.isNotEmpty()) {
                try {
                    group.javaClass.getMethod("setReferencedIds", IntArray::class.java).invoke(group, keep)
                } catch (_: Throwable) {
                    // 拿不到 Group 引用列表时仍先隐藏，最坏情况是下次布局被还原
                }
            }
        }
        setting.visibility = View.GONE
        return true
    }
}
