#requires -Version 5.1
<#
XGIMI TV debloat helper (reversible, opt-in).

Default action is READ-ONLY listing. Destructive actions require -Action and -Yes.
Reversal: pm uninstall --user 0 <pkg>  ->  cmd package install-existing <pkg>
          pm disable-user --user 0 <pkg> -> pm enable <pkg>

Critical packages (com.xgimi.*, launcher, settings, OTA, remote, display, input,
wifi/bt, media/codec, tvprovider, google, android core) are always refused.
#>

param(
    [ValidateSet('List', 'Uninstall', 'Disable', 'Enable', 'Restore')]
    [string]$Action = 'List',

    [string]$Serial = '',

    [string]$Adb = '',

    # Comma-separated package names, e.g. com.dangbeimarket,com.shafa.market
    [string[]]$Packages = @(),

    [string]$BackupDir = '',

    # Allow uninstalling /data/app (third-party) packages. APK is backed up first.
    # Without this switch, Uninstall refuses non-system packages; use Disable instead.
    [switch]$AllowApkRemoval,

    [switch]$Yes
)

$ErrorActionPreference = 'Stop'

function Write-Step([string]$msg) { Write-Host "[*] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "[+] $msg" -ForegroundColor Green }
function Write-Warn2([string]$msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }
function Write-Err2([string]$msg) { Write-Host "[x] $msg" -ForegroundColor Red }

function Resolve-Adb {
    if ($Adb -and (Test-Path $Adb)) { return $Adb }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $fallback) { return $fallback }
    throw 'adb not found. Pass -Adb <path to adb.exe> or add it to PATH.'
}

function Get-Devices([string]$adb) {
    $out = & $adb devices 2>&1
    $devices = @()
    foreach ($line in $out) {
        if ($line -match '^(\S+)\s+device$') { $devices += $Matches[1] }
    }
    return $devices
}

function Resolve-Serial([string]$adb, [string]$requested) {
    $devices = @(Get-Devices $adb)
    if ($devices.Count -eq 0) {
        throw 'No adb device. Enable Developer options + USB debugging on the TV, then connect USB or run: adb connect <tv-ip>:5555'
    }
    if ($requested) {
        if ($devices -notcontains $requested) { throw "Serial '$requested' not found. Devices: $($devices -join ', ')" }
        return $requested
    }
    if ($devices.Count -gt 1) { throw "Multiple devices: $($devices -join ', '). Pass -Serial <serial>." }
    return $devices[0]
}

function Invoke-Shell([string]$adb, [string]$serial, [string]$command) {
    $out = & $adb -s $serial shell $command 2>&1
    return ($out | Out-String).Trim()
}

# Packages that must never be touched by this helper.
$CriticalPatterns = @(
    '^android$',
    '^android\.',
    '^com\.android',
    '^com\.google',
    '^com\.xgimi',
    '^com\.mitv',
    'launcher',
    'settings',
    'systemui',
    'inputmethod',
    'keyboard',
    'remote',
    '\bota\b',
    'upgrade',
    'update',
    'wifi',
    'bluetooth',
    'audio',
    'media',
    'codec',
    'drm',
    'tvprovider',
    'leanback',
    'setupwizard',
    'packageinstaller',
    'permissioncontroller',
    'telephony',
    'providers',
    'keystone',
    'focus',
    'display',
    'projector'
)

# Common TV bloat packages (display names only; not required for classification).
$KnownBloat = @{
    'com.hunantv.imgo.activity'      = 'Mango TV'
    'com.ktcp.video'                 = 'Tencent video (cloud)'
    'com.cibn.tv'                    = 'CIBN'
    'com.xiaodianshi.tv.yst'         = 'Bilibili TV'
    'com.qiyi.video.speeder'         = 'iQIYI TV'
    'com.tudou.tv'                   = 'Youku TV'
    'com.dangbeimarket'              = 'Dangbei Market'
    'com.shafa.market'               = 'Shafa Market'
    'com.hpplay.happyplay.aw'        = 'LeBo screen cast'
    'com.wukongtv.wkremote.client'   = 'Wukong remote'
    'com.qihoo.tv'                   = '360 TV'
    'com.pplive.androidxl'           = 'PPTV'
    'com.sohu.tv'                    = 'Sohu TV'
    'com.lekan.video'                = 'LeKan'
}

function Test-Critical([string]$name) {
    foreach ($p in $CriticalPatterns) {
        if ($name -match $p) { return $true }
    }
    return $false
}

function Get-Packages([string]$adb, [string]$serial) {
    $raw = Invoke-Shell $adb $serial 'pm list packages -f'
    $items = @()
    foreach ($line in ($raw -split "`r?`n")) {
        # APK paths may contain '=' (e.g. /data/app/~~xx==/base.apk=com.foo);
        # the package name is always after the LAST '='.
        if ($line -match '^package:(.+)=([^=\s]+)$') {
            $items += [pscustomobject]@{ Path = $Matches[1]; Name = $Matches[2] }
        }
    }
    return $items
}

function Get-HomePackages([string]$adb, [string]$serial) {
    $raw = Invoke-Shell $adb $serial 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    $homes = @()
    foreach ($line in ($raw -split "`r?`n")) {
        if ($line -match '^([A-Za-z0-9_.]+)/') { $homes += $Matches[1] }
    }
    return $homes
}

function Get-ThirdPartySet([string]$adb, [string]$serial) {
    $set = @{}
    $raw = Invoke-Shell $adb $serial 'pm list packages -3'
    foreach ($line in ($raw -split "`r?`n")) {
        if ($line -match '^package:([^\s]+)$') { $set[$Matches[1]] = $true }
    }
    return $set
}

function Show-List([string]$adb, [string]$serial) {
    $pkgs = Get-Packages $adb $serial
    $homes = @(Get-HomePackages $adb $serial)
    $thirdParty = Get-ThirdPartySet $adb $serial
    $rows = @()
    foreach ($p in $pkgs) {
        $kind = 'SYSTEM-REVIEW'
        if (Test-Critical $p.Name) { $kind = 'CRITICAL' }
        elseif ($KnownBloat.ContainsKey($p.Name)) { $kind = 'BLOAT' }
        elseif ($homes -contains $p.Name) { $kind = 'CRITICAL' }
        elseif ($thirdParty.ContainsKey($p.Name)) { $kind = 'THIRD-PARTY' }
        $rows += [pscustomobject]@{
            Package = $p.Name
            Kind    = $kind
            Label   = if ($KnownBloat.ContainsKey($p.Name)) { $KnownBloat[$p.Name] } else { '' }
            Apk     = $p.Path
        }
    }
    $rows = $rows | Sort-Object Kind, Package
    $rows | Format-Table -AutoSize -Wrap

    if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
    $csv = Join-Path $BackupDir ('packages-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.csv')
    $rows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $csv
    Write-Ok "Full list saved: $csv"
    Write-Warn2 'Only BLOAT/THIRD-PARTY candidates are reviewed. CRITICAL is refused by this tool.'
    Write-Warn2 'Never remove the home/launcher package, com.xgimi.*, OTA, remote, display or keystone services.'
}

function Confirm-Yes([string]$what) {
    if ($Yes) { return $true }
    $answer = Read-Host "About to $what. Type YES to continue"
    return ($answer -ceq 'YES')
}

function Test-SystemPath([string]$path) {
    $p = $path.Trim()
    return ($p -match '^/(system|system_ext|vendor|product|odm|apex)/')
}

function Backup-Apk([string]$adb, [string]$serial, [string]$pkg, [string]$dir, [string]$pathsRaw) {
    $target = Join-Path $dir ($pkg -replace '[^A-Za-z0-9_.-]', '_')
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    $i = 0
    foreach ($line in ($pathsRaw -split "`r?`n")) {
        if ($line -match '^package:(.+)$') {
            $remote = $Matches[1].Trim()
            $i++
            $local = Join-Path $target ("$i-" + (($remote -split '/')[-1]))
            Write-Step "pull $remote -> $local"
            & $adb -s $serial pull $remote $local | Out-Null
        }
    }
    return $i
}

function Backup-Package([string]$adb, [string]$serial, [string]$pkg, [string]$dir) {
    $safe = $pkg -replace '[^A-Za-z0-9_.-]', '_'
    try {
        $path = Invoke-Shell $adb $serial "pm path $pkg"
        $dump = Invoke-Shell $adb $serial "dumpsys package $pkg"
        Set-Content -Path (Join-Path $dir "$safe.path.txt") -Value $path -Encoding UTF8
        Set-Content -Path (Join-Path $dir "$safe.dump.txt") -Value $dump -Encoding UTF8
        Add-Content -Path (Join-Path $dir 'removed.txt') -Value $pkg -Encoding UTF8
    } catch {
        Write-Warn2 "Backup failed for $pkg : $($_.Exception.Message)"
    }
}

function Assert-Safe([string]$pkg, [string[]]$homes) {
    if (Test-Critical $pkg) { throw "Refused (critical pattern): $pkg" }
    if ($homes -contains $pkg) { throw "Refused (current launcher): $pkg" }
}

function Do-Uninstall([string]$adb, [string]$serial, [string[]]$names, [string]$dir) {
    $homes = @(Get-HomePackages $adb $serial)
    foreach ($pkg in $names) {
        $pkg = $pkg.Trim()
        if (-not $pkg) { continue }
        try { Assert-Safe $pkg $homes } catch { Write-Err2 $_.Exception.Message; continue }
        $paths = Invoke-Shell $adb $serial "pm path $pkg"
        $isSystem = Test-SystemPath $paths
        if (-not $isSystem -and -not $AllowApkRemoval) {
            Write-Err2 "$pkg is /data/app (third-party): uninstall deletes the APK and install-existing cannot restore it. Use -Action Disable, or pass -AllowApkRemoval to back up the APK first."
            continue
        }
        if (-not (Confirm-Yes "uninstall (current user) $pkg")) { Write-Warn2 "Skipped $pkg"; continue }
        Backup-Package $adb $serial $pkg $dir
        if (-not $isSystem) {
            $count = Backup-Apk $adb $serial $pkg $dir $paths
            Write-Warn2 "APK backup: $count file(s) under $dir"
        }
        Write-Step "pm uninstall --user 0 $pkg"
        $out = Invoke-Shell $adb $serial "pm uninstall --user 0 $pkg"
        if ($out -match '^Success') {
            if ($isSystem) { Write-Ok "$pkg removed (current user). Restore: cmd package install-existing $pkg" }
            else { Write-Ok "$pkg uninstalled. Restore: -Action Restore (reinstalls backed-up APK)" }
        }
        else { Write-Err2 "$pkg -> $out" }
    }
}

function Do-Disable([string]$adb, [string]$serial, [string[]]$names, [string]$dir) {
    $homes = @(Get-HomePackages $adb $serial)
    foreach ($pkg in $names) {
        $pkg = $pkg.Trim()
        if (-not $pkg) { continue }
        try { Assert-Safe $pkg $homes } catch { Write-Err2 $_.Exception.Message; continue }
        if (-not (Confirm-Yes "disable-user $pkg")) { Write-Warn2 "Skipped $pkg"; continue }
        Backup-Package $adb $serial $pkg $dir
        Write-Step "pm disable-user --user 0 $pkg"
        $out = Invoke-Shell $adb $serial "pm disable-user --user 0 $pkg"
        if ($out -match 'disabled-user|new state') { Write-Ok "$pkg disabled. Re-enable: pm enable $pkg" }
        else { Write-Err2 "$pkg -> $out" }
    }
}

function Do-Enable([string]$adb, [string]$serial, [string[]]$names) {
    foreach ($pkg in $names) {
        $pkg = $pkg.Trim()
        if (-not $pkg) { continue }
        Write-Step "pm enable $pkg"
        $out = Invoke-Shell $adb $serial "pm enable $pkg"
        Write-Ok "$pkg -> $out"
    }
}

function Do-Restore([string]$adb, [string]$serial, [string[]]$names, [string]$dir) {
    $list = @()
    if ($names.Count -gt 0) { $list = $names }
    elseif (Test-Path (Join-Path $dir 'removed.txt')) {
        $list = Get-Content (Join-Path $dir 'removed.txt') | Where-Object { $_.Trim() }
    }
    $list = @($list | Where-Object { $_.Trim() } | Select-Object -Unique)
    if ($list.Count -eq 0) { Write-Warn2 'Nothing to restore (removed.txt empty).'; return }
    foreach ($pkg in $list) {
        $pkg = $pkg.Trim()
        if (-not $pkg) { continue }
        Write-Step "cmd package install-existing $pkg"
        $out = Invoke-Shell $adb $serial "cmd package install-existing $pkg"
        if ($out -notmatch 'NameNotFoundException|Error') {
            Write-Ok "$pkg -> $out"
            continue
        }
        # Third-party package: fall back to the backed-up APKs.
        $apkDir = Join-Path $dir ($pkg -replace '[^A-Za-z0-9_.-]', '_')
        if (Test-Path $apkDir) {
            $apks = @(Get-ChildItem -Path $apkDir -Filter *.apk | Sort-Object Name)
            if ($apks.Count -gt 0) {
                if ($apks.Count -eq 1) {
                    Write-Step "adb install -r $pkg"
                    $installArgs = @('-s', $serial, 'install', '-r', $apks[0].FullName)
                } else {
                    Write-Step "adb install-multiple $pkg ($($apks.Count) apk)"
                    $installArgs = @('-s', $serial, 'install-multiple', '-r') + ($apks | ForEach-Object { $_.FullName })
                }
                $installOut = & $adb @installArgs 2>&1
                Write-Ok "$pkg -> $($installOut | Out-String)"
                continue
            }
        }
        Write-Err2 "$pkg restore failed: $out (no APK backup found)"
    }
}

# ---------------- main ----------------
$adbPath = Resolve-Adb
$serial = Resolve-Serial $adbPath $Serial
if (-not $BackupDir) {
    $BackupDir = Join-Path (Get-Location) ('xgimi-debloat-backup\' + $serial)
}
if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }
Write-Ok "adb: $adbPath"
Write-Ok "device: $serial"
Write-Ok "backup: $BackupDir"

switch ($Action) {
    'List'      { Show-List $adbPath $serial }
    'Uninstall' { Do-Uninstall $adbPath $serial $Packages $BackupDir }
    'Disable'   { Do-Disable $adbPath $serial $Packages $BackupDir }
    'Enable'    { Do-Enable $adbPath $serial $Packages }
    'Restore'   { Do-Restore $adbPath $serial $Packages $BackupDir }
}
