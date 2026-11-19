param(
    [string]$McDir = "C:\Users\zyh18\AppData\Roaming\.minecraft",
    [string]$Version = "neoforge-26.3.0.113",
    [string]$JavaExe = "C:\Users\zyh18\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\bin\java.exe",
    [string]$OutFile = "E:\AI Work\XiaoYi\MC Mod\dist\launch.ps1"
)

$ErrorActionPreference = "Stop"

function Load-Json($path) {
    return Get-Content $path -Raw | ConvertFrom-Json
}

# Collect version json chain
$chain = @()
$cur = $Version
while ($cur) {
    $jp = Join-Path $McDir "versions\$cur\$cur.json"
    if (-not (Test-Path $jp)) { throw "missing $jp" }
    $j = Load-Json $jp
    $chain += $j
    $cur = $j.inheritsFrom
}
Write-Host "version chain: $($chain | ForEach-Object { $_.id } | Select-Object -First 10)"

# Merge libraries (later entries in chain = parents; child overrides)
$libs = @{}
for ($i = $chain.Count - 1; $i -ge 0; $i--) {
    $j = $chain[$i]
    if ($j.libraries) {
        foreach ($l in $j.libraries) {
            $libs[$l.name] = $l
        }
    }
}
Write-Host "libraries: $($libs.Count)"

$cpEntries = New-Object System.Collections.Generic.List[string]
foreach ($k in $libs.Keys) {
    $l = $libs[$k]
    $path = $null
    if ($l.downloads -and $l.downloads.artifact -and $l.downloads.artifact.path) {
        $path = Join-Path (Join-Path $McDir "libraries") $l.downloads.artifact.path
    }
    else {
        # derive from maven coords group:artifact:version[:classifier]
        $parts = $k.Split(':')
        $grp = $parts[0] -replace '\.', '/'
        $art = $parts[1]
        $ver = $parts[2]
        $cls = if ($parts.Count -gt 3) { "-" + $parts[3] } else { "" }
        $path = Join-Path (Join-Path $McDir "libraries") "$grp/$art/$ver/$art-$ver$cls.jar"
    }
    if (Test-Path $path) { $cpEntries.Add($path) }
    else { Write-Host "MISSING LIB: $k -> $path" }
}

# natives dir for this version
$nativeDir = Join-Path $McDir "versions\$Version\natives"
if (-not (Test-Path $nativeDir)) { New-Item -ItemType Directory $nativeDir -Force | Out-Null }

# game jar of the base version
$baseId = $chain[$chain.Count - 1].id
$gameJar = Join-Path $McDir "versions\$baseId\$baseId.jar"
if (-not (Test-Path $gameJar)) { throw "missing game jar $gameJar" }
$cpEntries.Add($gameJar)

# main class
$mainClass = $chain[0].mainClass
Write-Host "mainClass: $mainClass"

# game arguments from all chain entries
$gameArgs = New-Object System.Collections.Generic.List[string]
for ($i = $chain.Count - 1; $i -ge 0; $i--) {
    $ga = $chain[$i].arguments.game
    if (-not $ga) { continue }
    foreach ($a in $ga) {
        if ($a -is [string]) {
            if ($a -notmatch '^\$\{') { $gameArgs.Add($a) }
        }
        else {
            $rulesOk = $true
            if ($a.rules) {
                foreach ($r in $a.rules) {
                    if ($r.action -eq "allow" -and $r.os -and $r.os.name -and $r.os.name -ne "windows") { $rulesOk = $false }
                }
            }
            if ($rulesOk -and $a.value) {
                foreach ($v in $a.value) { if ($v -notmatch '^\$\{') { $gameArgs.Add($v) } }
            }
        }
    }
}

$assetsDir = Join-Path $McDir "assets"
$assetIndex = $chain[$chain.Count - 1].assetIndex.id
$classpath = $cpEntries -join ';'

$launchArgs = @(
    "-Xmx4G",
    "-Djava.library.path=$nativeDir",
    "-DlibraryDirectory=$McDir\libraries",
    "-Dminecraft.launcher.brand=PCL",
    "-Dminecraft.launcher.version=2131",
    "-Dfml.ignoreInvalidMinecraftCertificates=true",
    "-Dfml.ignorePatchDiscrepancies=true",
    "-Dlog4j2.formatMsgNoLookups=true",
    "-Dstdout.encoding=utf-8",
    "-Dstderr.encoding=utf-8",
    "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
    "--enable-native-access=ALL-UNNAMED",
    "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "-cp", $classpath,
    $mainClass
) + $gameArgs + @(
    "--username", "Dev",
    "--version", $Version,
    "--gameDir", (Join-Path $McDir "versions\$Version"),
    "--assetsDir", $assetsDir,
    "--assetIndex", $assetIndex,
    "--uuid", "00000000000000000000000000000001",
    "--accessToken", "0",
    "--userType", "legacy",
    "--versionType", "PCL"
)

# write a .bat for manual inspection
$bat = @()
$bat += "chcp 65001>nul"
$bat += "@echo off"
$bat += "`"$JavaExe`" " + (($launchArgs | ForEach-Object { if ($_ -match '[\s;]') { "`"$_`"" } else { $_ } }) -join " ")
Set-Content -Path $OutFile -Value $bat -Encoding UTF8
Write-Host "wrote $OutFile"
Write-Host "classpath entries: $($cpEntries.Count)"
