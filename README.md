# 生活记账本 (Life Ledger)

一款纯本地的 Android 记账应用。不联网、不上传、数据只保存在手机本地。

技术栈是最朴素的 **纯 Java + 原生 XML 布局**，不使用 Gradle，由 `build.ps1`
直接调用 Android SDK 命令行工具链（aapt2 / javac / d8 / zipalign / apksigner）打包 APK。

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
- **指纹锁**：用系统生物识别锁住应用

## 环境要求

构建脚本顶部的路径常量需要改成你本机的实际路径：

| 变量 | 说明 |
| --- | --- |
| `$jdk` | JDK 17 目录 |
| `$bt` | Android SDK build-tools 目录（提供 aapt2、d8、zipalign、apksigner） |
| `$jar` | 用于编译的 `android.jar`（API 34） |

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

脚本会依次完成编译资源、链接、javac、d8、zipalign、签名，最终在项目根目录
输出已签名的 `生活记账本_v2.0.apk`。

首次构建时会自动用 `keytool` 生成签名密钥到 `work/ks/lifebook.keystore`。
该目录与密钥已在 `.gitignore` 中排除，请注意自行备份密钥文件：**同一个密钥
才能覆盖安装升级，密钥丢失后旧版本无法直接升级。**

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
build.ps1                      一键构建打包脚本
tools/                         IconGen、AddDex 两个构建辅助工具
```

## 隐私

应用不申请网络权限，所有账单数据存放在应用私有目录下的 SQLite 数据库中，
备份与 Excel 导出都在本机完成。
