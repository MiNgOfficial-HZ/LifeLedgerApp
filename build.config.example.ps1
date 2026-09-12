# 本机构建配置模板
#
# 用法：把本文件复制成 build.config.ps1 放在项目根目录，改成你自己的路径。
# build.config.ps1 已在 .gitignore 中，不会被提交，可以放心写本机路径。
#
# 优先级：命令行参数 > build.config.ps1 > 环境变量 > 自动探测。
# 只有当自动探测找不到工具链时才需要配；配好之后直接 .\build.ps1 即可构建。

# JDK 目录（需 JDK 11 及以上，推荐 17）
$Jdk = 'C:\Program Files\Java\jdk-17'

# Android SDK build-tools 目录（需含 aapt2.exe / d8.bat / zipalign.exe / apksigner.bat）
$BuildTools = 'C:\Android\Sdk\build-tools\34.0.0'

# 编译用 android.jar
$AndroidJar = 'C:\Android\Sdk\platforms\android-34\android.jar'

# 可选：签名密钥路径与口令，不填则用 work\ks\lifebook.keystore 与默认口令
# $Keystore        = 'D:\keys\my-release.keystore'
# $KeystorePassword = 'your-password'
# $KeyAlias         = 'my-alias'

# 可选：APK 输出目录，默认项目根目录
# $OutDir = 'D:\dist'
