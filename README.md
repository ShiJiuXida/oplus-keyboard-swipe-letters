# 小布26键上滑字母

> OPPO / 一加 ColorOS 小布输入法的 LSPosed 模块：自定义中文（拼音 / 五笔）与英文 26 键键盘的上滑字母 / 符号。

## 功能

- **中英文独立映射**：中文 26 键（拼音 / 五笔）与英文 26 键分别配置，互不影响
- **逐键自定义**：点按键帽即可设置该键的上滑符号，留空恢复默认，可单独禁用某一键的上滑
- **一键恢复 / 清空**：恢复默认映射或清空全部按键
- **配置导入导出**：配置以 JSON 文件导出 / 导入，换机无忧
- **保存即时生效**：保存后输入法自动重启应用新配置
- **玻璃质感界面**：COUI-Kit 液态玻璃 Tab 与页面切换动效，跟随系统深色模式

## 使用要求

- 已获取 Root，安装 [LSPosed](https://github.com/LSPosed/LSPosed) 2.2 及以上（或支持 libxposed API 102 的框架）
- 在框架中启用本模块，作用域勾选「小布输入法」

## 安装与使用

1. 从 [Actions](../../actions) 构建或 Releases 下载 APK 安装
2. 在 LSPosed 中启用模块，作用域选择小布输入法（`com.oplus.keyboard`）
3. 打开模块，进入「中文26键 / 英文26键」设置上滑符号
4. 点击底部「保存并应用配置」，输入法自动重启生效

## 构建

```bash
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/debug/`。仓库自带 GitHub Actions 构建（Actions → Build Debug APK → Run workflow），可在触发时自定义版本号与安装包名称，产物为 zip（内含 debug 签名 APK，可直接安装）。

## 致谢

- [COUI-Kit](https://github.com/ItosEO/COUI-Kit) · 界面框架与液态玻璃效果
- [libxposed/api](https://github.com/libxposed/api) · [libxposed/service](https://github.com/libxposed/service) · 模块运行框架

## 赞赏支持

如果这个模块对你有帮助，欢迎请作者喝杯奶茶 ₍ᐢ..ᐢ₎♡

<div align="center">
  <img src="donate/donate_v.png" width="220" alt="赞赏码" />
  <img src="donate/donate_z.png" width="220" alt="赞赏码" />
</div>

## 许可

[Apache License 2.0](LICENSE)
