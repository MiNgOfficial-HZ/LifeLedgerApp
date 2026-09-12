<#
.SYNOPSIS
    生活记账本 —— 一键编译并签名 APK。

.DESCRIPTION
    纯 Java + 原生 XML 的安卓工程，不依赖 Gradle，直接调用 Android SDK 命令行工具链
    （aapt2 / javac / d8 / zipalign / apksigner）完成编译与签名。

    工具链路径按以下优先级逐级解析，先命中者生效：
      1. 命令行参数       -Jdk / -BuildTools / -AndroidJar / -Keystore
      2. 本地配置文件     build.config.ps1（可选，已加入 .gitignore，不随仓库分发）
      3. 专用环境变量     LIFELEDGER_JDK / LIFELEDGER_BUILD_TOOLS / LIFELEDGER_ANDROID_JAR
      4. 通用环境变量     JAVA_HOME / ANDROID_HOME / ANDROID_SDK_ROOT
      5. 常见安装目录探测  各 Java 发行版目录、Android Studio 自带 JBR、标准 SDK 目录

    必需项解析失败时，脚本会直接指出缺什么、用哪个参数补，无需修改脚本本身。

.PARAMETER ProjectRoot
    项目根目录，默认取本脚本所在目录。

.PARAMETER Jdk
    JDK 目录，需 JDK 11 及以上（脚本按 -source/-target 11 编译，推荐 JDK 17）。

.PARAMETER BuildTools
    Android SDK build-tools 目录，需包含 aapt2、d8、zipalign、apksigner。

.PARAMETER AndroidJar
    编译用的 android.jar，通常位于 <sdk>/platforms/android-34/android.jar。

.PARAMETER OutDir
    签名 APK 的输出目录，默认项目根目录；文件名按清单里的 versionName 自动生成。

.PARAMETER Keystore
    签名密钥路径，默认 work/ks/lifebook.keystore，不存在时自动用 keytool 生成。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\build.ps1

.EXAMPLE
    .\build.ps1 -Jdk "C:\jdk-17.0.20.1+1" `
                -BuildTools "C:\Android\Sdk\build-tools\34.0.0" `
                -AndroidJar "C:\Android\Sdk\platforms\android-34\android.jar"

.EXAMPLE
    # CI 或长期本机配置可以用环境变量代替参数
    $env:JAVA_HOME = "C:\jdk-17"; $env:ANDROID_HOME = "C:\Android\Sdk"; .\build.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot,
    [string]$Jdk,
    [string]$BuildTools,
    [string]$AndroidJar,
    [string]$OutDir,
    [string]$Keystore,
    [string]$KeystorePassword,
    [string]$KeyAlias
)

$ErrorActionPreference = 'Stop'

# 显式传入的命令行参数优先级最高：先记住它们，读完本地配置后再恢复，避免被配置覆盖。
$configurableKeys = @('ProjectRoot','Jdk','BuildTools','AndroidJar','OutDir','Keystore','KeystorePassword','KeyAlias')
$explicitValue = @{}
foreach ($key in $configurableKeys) {
    if ($PSBoundParameters.ContainsKey($key)) { $explicitValue[$key] = Get-Variable -Name $key -ValueOnly }
}

$scriptRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptRoot)) { $scriptRoot = (Get-Location).ProviderPath }

# 可选的本地配置：把本机工具链路径写在这里，既能零参数构建，又不会把机器路径提交进仓库。
$configPath = Join-Path $scriptRoot 'build.config.ps1'
if (Test-Path -LiteralPath $configPath) {
    . $configPath
    Write-Output "[i] 已加载本地配置 $configPath"
}

foreach ($key in $explicitValue.Keys) { Set-Variable -Name $key -Value $explicitValue[$key] }

if (-not $ProjectRoot) { $ProjectRoot = $scriptRoot }
if (-not $KeystorePassword) { $KeystorePassword = 'lifebook2025' }
if (-not $KeyAlias) { $KeyAlias = 'lifebook' }


# ---------------------------------------------------------------------------
# 路径解析
# ---------------------------------------------------------------------------

function Get-DirCandidates {
    param([string[]]$Patterns, [switch]$VersionDescending)

    $found = New-Object System.Collections.Generic.List[string]
    foreach ($pattern in $Patterns) {
        if ([string]::IsNullOrWhiteSpace($pattern)) { continue }

        # 不含通配符的就是明确指定的目录：必须原样采用。
        # 注意不能用 Get-ChildItem，因为它对字面目录路径会枚举子项而不是返回该目录本身。
        if ($pattern -notmatch '[\*\?\[]') {
            try { $found.Add((Resolve-Path -LiteralPath $pattern -ErrorAction Stop).ProviderPath) } catch { }
            continue
        }

        $dirs = @(Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue)
        if ($VersionDescending) {
            # 按名称里的首个数字降序，保证 jdk-17 排在 jdk-8 前面、build-tools 34 排在 29 前面
            $dirs = @($dirs | Sort-Object -Property @{
                Expression = { if ($_.Name -match '(\d+)') { [int]$Matches[1] } else { 0 } }
            } -Descending)
        } else {
            $dirs = @($dirs | Sort-Object Name -Descending)
        }
        foreach ($d in $dirs) { $found.Add($d.FullName) }
    }
    return $found
}

function Select-ToolPath {
    param(
        [string[]]$Candidates,
        [string]$Probe,
        [string]$Label,
        [string]$Hint
    )

    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $resolved = $null
        try { $resolved = (Resolve-Path -LiteralPath $candidate -ErrorAction Stop).ProviderPath } catch { continue }
        if (-not (Test-Path -LiteralPath $resolved)) { continue }
        if ([string]::IsNullOrEmpty($Probe)) { return $resolved }
        if (Test-Path -LiteralPath (Join-Path $resolved $Probe)) { return $resolved }
    }
    throw "找不到 $Label。$Hint"
}

# 清单里的 targetSdkVersion / versionName 决定用哪个 android.jar、产物叫什么名字
$appDir = Join-Path $ProjectRoot 'app'
$work = Join-Path $ProjectRoot 'work'
$manifestPath = Join-Path $appDir 'AndroidManifest.xml'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "未找到 $manifestPath。请检查 -ProjectRoot 是否指向项目根目录。"
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8

$targetSdk = 34
if ($manifest -match 'targetSdkVersion\s*=\s*"(\d+)"') { $targetSdk = [int]$Matches[1] }

$versionName = 'dev'
if ($manifest -match 'versionName\s*=\s*"([^"]+)"') { $versionName = $Matches[1] }

# 命令行显式指定的路径必须是有效的：这种情况下静默回退到别的工具链会让人误以为用的是自己指定的那套。
# 配置文件和环境变量则允许探测失败后继续往下一级回退。
function Assert-ExplicitPath {
    param([string]$Key, [string]$Probe, [string]$Label)
    # 注意：这里不能用 $PSBoundParameters，它在函数作用域里只反映函数自己的参数。
    # $explicitValue 记录的就是命令行显式传入的那些键，正是我们要判断的集合。
    if (-not $explicitValue.ContainsKey($Key)) { return }
    $value = $explicitValue[$Key]
    if ([string]::IsNullOrWhiteSpace($value)) { return }
    $missing = if ([string]::IsNullOrEmpty($Probe)) { "路径不存在" } else { "其中没有 $Probe" }
    $ok = $false
    try {
        $resolved = (Resolve-Path -LiteralPath $value -ErrorAction Stop).ProviderPath
        $ok = if ([string]::IsNullOrEmpty($Probe)) { Test-Path -LiteralPath $resolved } else { Test-Path -LiteralPath (Join-Path $resolved $Probe) }
    } catch { $ok = $false }
    if (-not $ok) { throw "-$Key 指定的$Label 无效：$value（$missing）。" }
}

# --- JDK ---
Assert-ExplicitPath -Key 'Jdk' -Probe 'bin\javac.exe' -Label 'JDK 目录'

$jdkPatterns = @()
if ($Jdk) { $jdkPatterns += $Jdk }
if (Test-Path env:LIFELEDGER_JDK) { $jdkPatterns += $env:LIFELEDGER_JDK }
if (Test-Path env:JAVA_HOME) { $jdkPatterns += $env:JAVA_HOME }
$jdkPatterns += @(
    "$env:ProgramFiles\Java\jdk*",
    "$env:ProgramFiles\Eclipse Adoptium\jdk*",
    "$env:ProgramFiles\Microsoft\jdk*",
    "$env:ProgramFiles\Amazon Corretto\jdk*",
    "$env:ProgramFiles\Zulu\zulu*",
    "$env:ProgramFiles\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk*",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
)
$jdkCandidates = @(Get-DirCandidates -Patterns $jdkPatterns -VersionDescending)
$javacOnPath = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($javacOnPath) { $jdkCandidates += (Split-Path (Split-Path $javacOnPath.Source -Parent) -Parent) }
$jdkHome = Select-ToolPath -Candidates $jdkCandidates -Probe 'bin\javac.exe' -Label 'JDK' `
    -Hint '请用 -Jdk 指向 JDK 安装目录，或设置 JAVA_HOME / LIFELEDGER_JDK 环境变量。'

# --- Android SDK ---
$sdkRoots = New-Object System.Collections.Generic.List[string]
if (Test-Path env:ANDROID_HOME) { $sdkRoots.Add($env:ANDROID_HOME) }
if (Test-Path env:ANDROID_SDK_ROOT) { $sdkRoots.Add($env:ANDROID_SDK_ROOT) }
$sdkRoots.Add("$env:LOCALAPPDATA\Android\Sdk")
$sdkRoots.Add("$env:ProgramFiles\Android\Sdk")

$btPatterns = @()
Assert-ExplicitPath -Key 'BuildTools' -Probe 'aapt2.exe' -Label 'build-tools 目录'

if ($BuildTools) { $btPatterns += $BuildTools }
if (Test-Path env:LIFELEDGER_BUILD_TOOLS) { $btPatterns += $env:LIFELEDGER_BUILD_TOOLS }
foreach ($root in $sdkRoots) { $btPatterns += (Join-Path $root 'build-tools\*') }
$btCandidates = @(Get-DirCandidates -Patterns $btPatterns -VersionDescending)
$buildToolsDir = Select-ToolPath -Candidates $btCandidates -Probe 'aapt2.exe' -Label 'Android build-tools' `
    -Hint '请用 -BuildTools 指向 build-tools 目录（需含 aapt2.exe），或设置 ANDROID_HOME / LIFELEDGER_BUILD_TOOLS 环境变量。'

$jarPatterns = @()
Assert-ExplicitPath -Key 'AndroidJar' -Probe '' -Label 'android.jar 文件'

if ($AndroidJar) { $jarPatterns += $AndroidJar }
if (Test-Path env:LIFELEDGER_ANDROID_JAR) { $jarPatterns += $env:LIFELEDGER_ANDROID_JAR }
foreach ($root in $sdkRoots) { $jarPatterns += (Join-Path $root "platforms\android-$targetSdk\android.jar") }
$platformDirs = @(Get-DirCandidates -Patterns ($sdkRoots | ForEach-Object { Join-Path $_ 'platforms\android-*' }) -VersionDescending)
foreach ($dir in $platformDirs) { $jarPatterns += (Join-Path $dir 'android.jar') }
$androidJarPath = Select-ToolPath -Candidates $jarPatterns -Probe '' -Label 'android.jar' `
    -Hint "请用 -AndroidJar 指定 android.jar，或设置 ANDROID_HOME / LIFELEDGER_ANDROID_JAR 环境变量（清单里 targetSdkVersion=$targetSdk）。"

# --- 签名密钥 ---
if (-not $Keystore) {
    $Keystore = Join-Path $work 'ks\lifebook.keystore'
} elseif (-not [System.IO.Path]::IsPathRooted($Keystore)) {
    $Keystore = Join-Path $ProjectRoot $Keystore
}

if (-not $OutDir) { $OutDir = $ProjectRoot }
$final = Join-Path $OutDir "生活记账本_v$versionName.apk"

# --- 工具可执行文件 ---
$javacExe     = Join-Path $jdkHome 'bin\javac.exe'
$javaExe      = Join-Path $jdkHome 'bin\java.exe'
$keytoolExe   = Join-Path $jdkHome 'bin\keytool.exe'
$aapt2Exe     = Join-Path $buildToolsDir 'aapt2.exe'
$d8Bat        = Join-Path $buildToolsDir 'd8.bat'
$zipalignExe  = Join-Path $buildToolsDir 'zipalign.exe'
$apksignerBat = Join-Path $buildToolsDir 'apksigner.bat'

Write-Output "[i] 项目根目录  : $ProjectRoot"
Write-Output "[i] JDK         : $jdkHome"
Write-Output "[i] build-tools : $buildToolsDir"
Write-Output "[i] android.jar : $androidJarPath"
Write-Output "[i] 签名密钥    : $Keystore"
Write-Output "[i] 输出文件    : $final"


# ---------------------------------------------------------------------------
# 构建
# ---------------------------------------------------------------------------

$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $jdkHome 'bin') + ';' + $env:PATH

$genDir = Join-Path $work 'gen'
$classesDir = Join-Path $work 'classes'
$dexOutDir = Join-Path $work 'dex-out'

# 清掉上一轮产物，避免已删除的源文件残留 .class 混进 APK
Remove-Item $genDir, $classesDir, $dexOutDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $work 'compiled.zip'), (Join-Path $work 'base.apk'), (Join-Path $work 'withdex.apk'), (Join-Path $work 'aligned.apk') -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work, $genDir, $classesDir, $dexOutDir | Out-Null

Write-Output "[0/8] compile tools (IconGen / AddDex)..."
$toolsDir = Join-Path $ProjectRoot 'tools'
if (-not (Test-Path -LiteralPath (Join-Path $toolsDir 'AddDex.class'))) {
    & $javacExe -encoding UTF-8 (Join-Path $toolsDir 'IconGen.java') (Join-Path $toolsDir 'AddDex.java') -d $toolsDir
    if ($LASTEXITCODE -ne 0) { throw "tools compile failed" }
}
$launcherIcon = Join-Path $appDir 'res\drawable\ic_launcher.png'
if (-not (Test-Path -LiteralPath $launcherIcon)) {
    & $javaExe -cp $toolsDir IconGen $launcherIcon
    if ($LASTEXITCODE -ne 0) { throw "icon generation failed" }
}

Write-Output "[1/8] aapt2 compile resources..."
& $aapt2Exe compile --dir (Join-Path $appDir 'res') -o (Join-Path $work 'compiled.zip')
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Output "[2/8] aapt2 link (R.java + base.apk)..."
& $aapt2Exe link -o (Join-Path $work 'base.apk') -I $androidJarPath --manifest $manifestPath `
    -R (Join-Path $work 'compiled.zip') --auto-add-overlay --java $genDir
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "[3/8] javac..."
$src = @(
    Get-ChildItem (Join-Path $appDir 'src'), $genDir -Recurse -Filter *.java |
        ForEach-Object { $_.FullName }
)
& $javacExe -encoding UTF-8 -source 11 -target 11 -Xlint:-options -classpath $androidJarPath -d $classesDir $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "[4/8] d8 -> classes.dex..."
$cls = @(Get-ChildItem $classesDir -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& $d8Bat --min-api 26 --lib $androidJarPath --output $dexOutDir $cls
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "[5/8] add classes.dex (stored, uncompressed)..."
& $javaExe -cp $toolsDir AddDex (Join-Path $work 'base.apk') (Join-Path $work 'withdex.apk') (Join-Path $dexOutDir 'classes.dex')
if ($LASTEXITCODE -ne 0) { throw "AddDex failed" }

Write-Output "[6/8] zipalign..."
& $zipalignExe -f 4 (Join-Path $work 'withdex.apk') (Join-Path $work 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "[7/8] keystore + apksigner..."
if (-not (Test-Path -LiteralPath $Keystore)) {
    Write-Output "      未找到密钥，用 keytool 生成：$Keystore"
    if ($KeystorePassword -eq 'lifebook2025') {
        Write-Warning "正在使用默认密钥口令。要分发给别人时建议用 -KeystorePassword 指定自己的口令，并备份好密钥文件——密钥换了，已安装的旧版本就无法覆盖升级。"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Path $Keystore -Parent) | Out-Null
    & $keytoolExe -genkeypair -keystore $Keystore -alias $KeyAlias -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $KeystorePassword -keypass $KeystorePassword `
        -dname "CN=LifeLedger,OU=Dev,O=LifeLedger,L=Beijing,ST=Beijing,C=CN" -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
$out = Join-Path $work 'final-signed.apk'
Remove-Item $out -ErrorAction SilentlyContinue
& $apksignerBat sign --ks $Keystore --ks-pass "pass:$KeystorePassword" --key-pass "pass:$KeystorePassword" --out $out (Join-Path $work 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Output "[8/8] copy to output dir..."
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Remove-Item $final -ErrorAction SilentlyContinue
Copy-Item $out $final

Write-Output "=== verify signature ==="
& $apksignerBat verify --print-certs $out
Write-Output "=== result ==="
Get-Item $final | Select-Object FullName, Length, LastWriteTime
