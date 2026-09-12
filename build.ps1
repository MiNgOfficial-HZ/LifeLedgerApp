$ErrorActionPreference = 'Stop'
$b = "E:/LifeLedgerApp"
$jdk = "E:/express_app_build/tools/jdk/jdk-17.0.20.1+1"
$bt  = "E:/express_app_build/tools/android-14"
$jar = "E:/express_app_build/tools/android-13/android.jar"

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk/bin;$env:PATH"

$appDir = "$b/app"
$work = "$b/work"
New-Item -ItemType Directory -Force -Path "$work/gen","$work/classes","$work/dex-out","$work/ks" | Out-Null
Remove-Item "$work/compiled.zip","$work/base.apk","$work/withdex.apk","$work/aligned.apk" -ErrorAction SilentlyContinue

Write-Output "[0/8] compile tools (IconGen / AddDex)..."
if (-not (Test-Path "$b/tools/AddDex.class")) {
  & "$jdk/bin/javac.exe" -encoding UTF-8 "$b/tools/IconGen.java" "$b/tools/AddDex.java" -d "$b/tools"
  if ($LASTEXITCODE -ne 0) { throw "tools compile failed" }
}
if (-not (Test-Path "$appDir/res/drawable/ic_launcher.png")) {
  & "$jdk/bin/java.exe" -cp "$b/tools" IconGen "$appDir/res/drawable/ic_launcher.png"
  if ($LASTEXITCODE -ne 0) { throw "icon generation failed" }
}

Write-Output "[1/8] aapt2 compile resources..."
& "$bt/aapt2.exe" compile --dir "$appDir/res" -o "$work/compiled.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Output "[2/8] aapt2 link (R.java + base.apk)..."
& "$bt/aapt2.exe" link -o "$work/base.apk" -I $jar --manifest "$appDir/AndroidManifest.xml" -R "$work/compiled.zip" --auto-add-overlay --java "$work/gen"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "[3/8] javac..."
$src = @(Get-ChildItem "$appDir/src","$work/gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& "$jdk/bin/javac.exe" -encoding UTF-8 -source 11 -target 11 -Xlint:-options -classpath $jar -d "$work/classes" $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "[4/8] d8 -> classes.dex..."
$cls = @(Get-ChildItem "$work/classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& "$bt/d8.bat" --min-api 26 --lib $jar --output "$work/dex-out" $cls
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "[5/8] add classes.dex (stored, uncompressed)..."
& "$jdk/bin/java.exe" -cp "$b/tools" AddDex "$work/base.apk" "$work/withdex.apk" "$work/dex-out/classes.dex"
if ($LASTEXITCODE -ne 0) { throw "AddDex failed" }

Write-Output "[6/8] zipalign..."
& "$bt/zipalign.exe" -f 4 "$work/withdex.apk" "$work/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Output "[7/8] keystore + apksigner..."
if (-not (Test-Path "$work/ks/lifebook.keystore")) {
  & "$jdk/bin/keytool.exe" -genkeypair -keystore "$work/ks/lifebook.keystore" -alias lifebook -keyalg RSA -keysize 2048 -validity 10000 -storepass lifebook2025 -keypass lifebook2025 -dname "CN=LifeLedger,OU=Dev,O=LifeLedger,L=Beijing,ST=Beijing,C=CN" -noprompt
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
$out = "$work/final-signed.apk"
Remove-Item $out -ErrorAction SilentlyContinue
& "$bt/apksigner.bat" sign --ks "$work/ks/lifebook.keystore" --ks-pass pass:lifebook2025 --key-pass pass:lifebook2025 --out $out "$work/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Output "[8/8] copy to project root..."
$final = "E:/LifeLedgerApp/生活记账本_v2.0.apk"
Remove-Item $final -ErrorAction SilentlyContinue
Copy-Item $out $final

Write-Output "=== verify signature ==="
& "$bt/apksigner.bat" verify --print-certs $out
Write-Output "=== result ==="
Get-Item $final | Select-Object FullName, Length, LastWriteTime
