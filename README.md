# Life Ledger

一款纯本地的 Android 记账应用。不联网、不上传、数据只保存在手机本地。

技术栈是最简单的 **纯 Java + 原生 XML 布局**，不使用 Gradle，由 `build.ps1`
直接调用 Android SDK 命令行工具链（aapt2 / javac / d8 / zipalign / apksigner）打包 APK。

## 下载安装

不想自己编译的话，直接到 [Releases](https://github.com/MiNgOfficial-HZ/LifeLedgerApp/releases/latest)
下载 `生活记账本_v2.0.apk`，传到手机上点击安装即可（需要在系统设置里允许「安装未知来源应用」）。

应用要求 Android 8.0（API 26）及以上，已针对大部分安卓机型做过适配，正常安装即用。

## 功能

- **多账本**：家庭、个人、旅行等账本分开记账，互不干扰
- **收支记录**：收入 / 支出 / 转账，支持分类、备注、日期与月份切换
- **账单分析**：饼图展示分类占比，柱状图对比月度收支趋势
- **预算管理**：按月设置预算，超支提醒
- **周期记账**：房租、工资、订阅等按日 / 周 / 月自动生成记录
- **旅行账本**：按行程归集消费，行程结束后可单独结算
- **分类管理**：自定义收支分类，支持图标与父子分类
- **回收站**：删除的记录可恢复，避免误删
- **导出 Excel**：按月导出流水，方便在电脑上继续整理
- **备份与恢复**：一键导出 / 导入 ZIP 备份，支持自动备份
- **账单提醒**：每天 / 每周 / 每月定时推送账单通知
- **指纹锁**：用系统生物识别锁住应用（拉起系统应用）

## 环境要求

| 依赖 | 说明 |
| --- | --- |
| 操作系统 | Windows（脚本使用 `d8.bat` / `apksigner.bat` 等批处理工具） |
| PowerShell | Windows PowerShell 5.1 或 PowerShell 7 均可 |
| JDK | JDK 11 及以上，推荐 17（脚本按 `-source/-target 11` 编译） |
| Android SDK build-tools | 需包含 `aapt2`、`d8`、`zipalign`、`apksigner` |
| android.jar | 对应 API 34 的平台包 |

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

脚本会依次完成编译资源、链接、javac、d8、zipalign、签名，最终在项目根目录输出已签名的
`生活记账本_v2.0.apk`（文件名跟随 `AndroidManifest.xml` 里的 `versionName`，改版本号即可自动改名）。

工具链路径不需要改脚本，按下面的优先级自动解析：

1. 命令行参数 `-Jdk` / `-BuildTools` / `-AndroidJar`
2. 本地配置文件 `build.config.ps1`
3. 环境变量 `LIFELEDGER_JDK` / `LIFELEDGER_BUILD_TOOLS` / `LIFELEDGER_ANDROID_JAR`
4. 环境变量 `JAVA_HOME` / `ANDROID_HOME` / `ANDROID_SDK_ROOT`
5. 常见安装目录自动探测（各 Java 发行版目录、Android Studio 自带 JBR、标准 SDK 目录）

大多数装了 Android Studio 的机器第 5 条就能直接命中，零配置即可构建。若探测不到，
脚本会明确提示缺哪一项、该用哪个参数补上。两种常用写法：

```powershell
# 方式一：命令行直接指定
.\build.ps1 -Jdk "C:\jdk-17.0.20.1+1" `
            -BuildTools "C:\Android\Sdk\build-tools\34.0.0" `
            -AndroidJar "C:\Android\Sdk\platforms\android-34\android.jar"

# 方式二：把本机路径写进 build.config.ps1（该文件已在 .gitignore 中），之后零参数构建
Copy-Item .\build.config.example.ps1 .\build.config.ps1
notepad .\build.config.ps1
.\build.ps1
```

首次构建时会自动用 `keytool` 生成签名密钥到 `work/ks/lifebook.keystore`。
该目录与密钥已在 `.gitignore` 中排除，请注意自行备份密钥文件：**同一个密钥
才能覆盖安装升级，密钥丢失后旧版本无法直接升级。**

签名相关也可配置：`-Keystore` 指定密钥路径，`-KeystorePassword` / `-KeyAlias` 指定口令与别名，
`-OutDir` 指定 APK 输出目录。想用自己的密钥发布时，建议显式传入，避免使用默认口令。

脚本会自动清理上一轮的 `work/gen`、`work/classes`、`work/dex-out`，避免已删除的源文件
残留 `.class` 混进 APK。

## 项目结构

```
app/
  AndroidManifest.xml          应用清单，minSdk 26 / targetSdk 34
  res/
    layout/                    页面与弹窗布局
    drawable/                  图标与背景（矢量 + 启动图标）
    values/ colors.xml 等      配色、样式、文案
    values-night/              深色模式配色
  src/com/lifebook/ledger/
    MainActivity.java          主界面与页面切换
    ShareProvider.java         备份文件分享用 FileProvider
    db/DbHelper.java           SQLite 建库、建表与全部数据访问
    model/                     账本、记录、分类、预算等数据模型
    page/                      首页 / 明细 / 分析 / 旅行 / 设置 各页
    ui/                        各类弹窗、列表适配器与管理页
    util/                      备份、Excel 导出、通知、加锁、小工具
    view/                      自绘饼图与柱状图
build.ps1                      一键构建打包脚本（工具链路径多级可配置）
build.config.example.ps1       本机路径配置模板，复制成 build.config.ps1 后生效
tools/                         IconGen、AddDex 两个构建辅助工具
```

## 隐私

应用不申请网络权限，所有账单数据存放在应用私有目录下的 SQLite 数据库中，
备份与 Excel 导出都在本机完成。
