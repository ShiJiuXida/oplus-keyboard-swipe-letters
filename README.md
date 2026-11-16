# 小布输入法 26 键上滑字母

OPPO / 一加 ColorOS 小布输入法（`com.oplus.keyboard`）的 LSPosed 模块：自定义中文（拼音 / 五笔）与英文 26 键键盘的上滑字母 / 符号。

基于 [libxposed API 102](https://github.com/libxposed/api)（LSPosed 2.2+ / Vector 2.1+）开发，界面使用 [COUI-Kit](https://github.com/ItosEO/COUI-Kit) 构建。

## 功能

- 中英文 26 键布局分别独立配置，一键切换编辑
- 每个字母键的上滑符号可自定义，留空即禁用该键的上滑输入
- 一键恢复默认映射
- 配置导出 / 导入（JSON 文件）
- 保存后输入法自动重启生效

## 使用要求

- 已获取 Root 并安装 LSPosed 2.2 及以上（或支持 libxposed API 102 的框架）
- 在框架中启用本模块，作用域勾选「小布输入法」

## 构建

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。仓库自带 GitHub Actions 构建（Actions → Build APK → Run workflow），可在构建时自定义版本号与安装包名称。

## 相关项目

- [libxposed/api](https://github.com/libxposed/api) · [libxposed/service](https://github.com/libxposed/service)
- [COUI-Kit](https://github.com/ItosEO/COUI-Kit)

## 许可

Apache License 2.0，详见 [LICENSE](LICENSE)。
