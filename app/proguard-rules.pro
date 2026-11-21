# Xposed 入口三件套：R8 会改写 java_init.list 里的类名，
# 这条规则丢了就是「模块装了但 LSPosed 不显示」，任何版本都不能删。
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class com.kbswipe.module.ModuleEntry { *; }
-keep class com.kbswipe.module.App { *; }

# 框架回调由 LSPosed 反射调用
-keep class * implements io.github.libxposed.api.XposedModuleInterface$* { *; }

# hook 端反射读取宿主类，保留签名与注解信息
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers enum * { *; }

# androidx.window 是 compileOnly（运行时由系统提供）
-dontwarn androidx.window.**
-dontwarn org.jetbrains.annotations.**
