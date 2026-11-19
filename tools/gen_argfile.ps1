param(
    [string]$McDir = "C:\Users\zyh18\AppData\Roaming\.minecraft",
    [string]$Version = "neoforge-26.3.0.113",
    [string]$JavaExe = "C:\Users\zyh18\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\bin\java.exe",
    [string]$OutDir = "E:\AI Work\XiaoYi\MC Mod\dist"
)

$ErrorActionPreference = "Stop"

function Load-Json($path) { return Get-Content $path -Raw | ConvertFrom-Json }

$chain = @()
$cur = $Version
while ($cur) {
    $jp = Join-Path $McDir "versions\$cur\$cur.json"
    if (-not (Test-Path $jp)) { throw "missing $jp" }
    $j = Load-Json $jp
    $chain += $j
    $cur = $j.inheritsFrom
}

$libs = [ordered]@{}
for ($i = $chain.Count - 1; $i -ge 0; $i--) {
    if ($chain[$i].libraries) {
        foreach ($l in $chain[$i].libraries) { $libs[$l.name] = $l }
    }
}

function Get-LibPath($key, $l) {
    if ($l.downloads -and $l.downloads.artifact -and $l.downloads.artifact.path) {
        return Join-Path (Join-Path $McDir "libraries") $l.downloads.artifact.path
    }
    $parts = $key.Split(':')
    $grp = $parts[0] -replace '\.', '/'
    $art = $parts[1]
    $ver = $parts[2]
    $cls = if ($parts.Count -gt 3) { "-" + $parts[3] } else { "" }
    return Join-Path (Join-Path $McDir "libraries") "$grp/$art/$ver/$art-$ver$cls.jar"
}

function Test-Allowed($l) {
    if (-not $l.rules) { return $true }
    $allow = $false
    foreach ($r in $l.rules) {
        $osMatch = $true
        if ($r.os -and $r.os.name) { $osMatch = ($r.os.name -eq "windows") }
        if ($r.os -and $r.os.arch) { $osMatch = $osMatch -and ($r.os.arch -eq "x86_64") }
        if ($osMatch) { if ($r.action -eq "allow") { $allow = $true } else { $allow = $false } }
    }
    return $allow
}

$nativeDir = Join-Path $McDir "versions\$Version\natives"
if (Test-Path $nativeDir) { Remove-Item $nativeDir -Recurse -Force -EA 0 }
New-Item -ItemType Directory $nativeDir -Force | Out-Null

$cp = New-Object System.Collections.Generic.List[string]
$natives = New-Object System.Collections.Generic.List[string]

foreach ($k in $libs.Keys) {
    $l = $libs[$k]
    if (-not (Test-Allowed $l)) { continue }
    $path = Get-LibPath $k $l
    if (-not (Test-Path $path)) { Write-Host "SKIP: $k"; continue }
    if ($k -match 'natives-windows') { $natives.Add($path) } else { $cp.Add($path) }
}

$baseId = $chain[$chain.Count - 1].id
$cp.Add((Join-Path $McDir "versions\$baseId\$baseId.jar"))

Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($n in $natives) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($n)
        foreach ($e in $zip.Entries) {
            if ($e.Name -eq "") { continue }
            if ($e.FullName -match '^META-INF/') { continue }
            $dest = Join-Path $nativeDir $e.FullName
            $dir = Split-Path $dest -Parent
            if (-not (Test-Path $dir)) { New-Item -ItemType Directory $dir -Force | Out-Null }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true)
        }
        $zip.Dispose()
    }
    catch { Write-Host "native extract failed: $n" }
}

$mainClass = $chain[0].mainClass
$gameArgs = New-Object System.Collections.Generic.List[string]
for ($i = $chain.Count - 1; $i -ge 0; $i--) {
    $ga = $chain[$i].arguments.game
    if (-not $ga) { continue }
    foreach ($a in $ga) {
        if ($a -is [string]) { if ($a -notmatch '^\$\{') { $gameArgs.Add($a) } }
        else {
            $ok = $true
            if ($a.rules) {
                foreach ($r in $a.rules) {
                    if ($r.action -eq "allow" -and $r.os -and $r.os.name -and $r.os.name -ne "windows") { $ok = $false }
                }
            }
            if ($ok -and $a.value) { foreach ($v in $a.value) { if ($v -notmatch '^\$\{') { $gameArgs.Add($v) } } }
        }
    }
}

$assetIndex = $chain[$chain.Count - 1].assetIndex.id
$classpath = ($cp -join ';')

$jvm = @(
    "-Xmx4G",
    "-XX:+UseG1GC",
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
)

$game = $gameArgs + @(
    "--username", "Dev",
    "--version", $Version,
    "--gameDir", $McDir,
    "--assetsDir", (Join-Path $McDir "assets"),
    "--assetIndex", $assetIndex,
    "--uuid", "00000000000000000000000000000001",
    "--accessToken", "0",
    "--userType", "legacy",
    "--versionType", "PCL",
    "--width", "1280",
    "--height", "720"
)

# Java @argfile: quote args containing spaces; backslashes in quotes need escaping on Windows
function Fmt-Arg($s) {
    if ($s -match '[\s]') {
        $esc = $s -replace '\\', '\\'
        return '"' + $esc + '"'
    }
    return $s
}

$allArgs = $jvm + $game
$lines = $allArgs | ForEach-Object { Fmt-Arg $_ }
$argFile = Join-Path $OutDir "run.args"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($argFile, [string[]]$lines, $utf8NoBom)

Write-Host "classpath entries: $($cp.Count)"
Write-Host "native jars: $($natives.Count)"
Write-Host "mainClass: $mainClass"
Write-Host "argfile: $argFile"
Write-Host "JAVA: $JavaExe"
