param(
    [string]$McDir = "C:\Users\zyh18\AppData\Roaming\.minecraft",
    [string]$Version = "neoforge-26.3.0.113",
    [string]$OutFile = "E:\AI Work\XiaoYi\MC Mod\dist\run_final.args",
    [switch]$IncludeVanillaJar
)

$ErrorActionPreference = "Stop"
function Load-Json($p) { return Get-Content $p -Raw | ConvertFrom-Json }

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
    $art = $parts[1]; $ver = $parts[2]
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
    if (-not (Test-Path $path)) { Write-Host "SKIP MISSING: $k"; continue }
    if ($k -match 'natives-windows') { $natives.Add($path) } else { $cp.Add($path) }
}

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
    } catch { Write-Host "native fail: $n" }
}

# game jar: NOT added to classpath in NeoForge 26.3 production mode (see below)
$patched = Join-Path $McDir "libraries\net\neoforged\minecraft-client-patched\26.3.0.113\minecraft-client-patched-26.3.0.113.jar"
$baseId = $chain[$chain.Count - 1].id
$vanilla = Join-Path $McDir "versions\$baseId\$baseId.jar"
if (Test-Path $patched) { Write-Host "patched client jar present (discovered via libraryDirectory)" }
else { Write-Host "WARNING: patched client jar missing" }

if ($IncludeVanillaJar -and (Test-Path $vanilla)) {
    $cp.Add($vanilla); Write-Host "appended vanilla jar to classpath (launcher-style)"
}

$mainClass = $chain[0].mainClass

# IMPORTANT: In NeoForge 26.3 production mode, the Minecraft jar and the NeoForge
# universal jar must NOT be on the classpath. GameLocator discovers them from
# -DlibraryDirectory. Putting them on the classpath switches FML into "merged jar"
# mode and fails with 'patched Minecraft jar is missing' / missing Minecraft-Dists.
Write-Host "classpath = loader libs only (no minecraft jar, no neoforge jar)"

# Only use the TOP entry's game args (NeoForge's --fml.* flags).
# The parent vanilla json uses ${...} placeholders; mixing filtered flags/values breaks arg pairing.
$gameArgs = New-Object System.Collections.Generic.List[string]
$ga = $chain[0].arguments.game
if ($ga) {
    foreach ($a in $ga) {
        if ($a -is [string]) { if ($a -notmatch '\$\{') { $gameArgs.Add($a) } }
        else {
            $ok = $true
            if ($a.rules) {
                foreach ($r in $a.rules) {
                    if ($r.action -eq "allow" -and $r.os -and $r.os.name -and $r.os.name -ne "windows") { $ok = $false }
                }
            }
            if ($ok -and $a.value) { foreach ($v in $a.value) { if ($v -notmatch '\$\{') { $gameArgs.Add($v) } } }
        }
    }
}

$assetIndex = $chain[$chain.Count - 1].assetIndex.id
$classpath = ($cp -join ';')

$jvm = @(
    "-Xmx4G", "-XX:+UseG1GC",
    "-Djava.library.path=$nativeDir",
    "-DlibraryDirectory=$McDir\libraries",
    "-Dminecraft.launcher.brand=PCL", "-Dminecraft.launcher.version=2131",
    "-Dfml.ignoreInvalidMinecraftCertificates=true",
    "-Dfml.ignorePatchDiscrepancies=true",
    "-Dlog4j2.formatMsgNoLookups=true",
    "-Dstdout.encoding=utf-8", "-Dstderr.encoding=utf-8",
    "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming",
    "--enable-native-access=ALL-UNNAMED",
    "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
    "-cp", $classpath, $mainClass
)

$game = $gameArgs + @(
    "--username", "Dev", "--version", $Version,
    "--gameDir", $McDir,
    "--assetsDir", (Join-Path $McDir "assets"),
    "--assetIndex", $assetIndex,
    "--uuid", "00000000000000000000000000000001",
    "--accessToken", "0", "--userType", "legacy", "--versionType", "PCL",
    "--width", "1280", "--height", "720"
)

function Fmt-Arg($s) {
    if ($s -match '\s') { return '"' + ($s -replace '\\','\\') + '"' }
    return $s
}

$all = $jvm + $game
$lines = $all | ForEach-Object { Fmt-Arg $_ }
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($OutFile, [string[]]$lines, $utf8)

Write-Host "cp entries: $($cp.Count)  natives: $($natives.Count)  mainClass: $mainClass"
Write-Host "argfile: $OutFile"
