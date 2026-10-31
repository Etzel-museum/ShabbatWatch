# Installs "Shabbat mode" on a Galaxy Watch over Wi-Fi and grants its permissions.
# The app already knows the Galaxy Watch settings it switches off on Shabbat; add
# -Calibrate only to teach it extra settings (e.g. on a different watch model).
#
# On the watch: Settings > About watch > Software > tap "Software version" 7 times,
# then Settings > Developer options > ADB debugging ON + Wireless debugging ON.
# Wireless debugging > "Pair new device" shows IP, pairing port and a 6-digit code;
# the main Wireless debugging screen shows the (different) connection port.
#
#   powershell -ExecutionPolicy Bypass -File install.ps1 -Ip 192.168.1.50 -Port 41234 -PairPort 37000 -Code 123456
#
# Already paired before? Leave out -PairPort/-Code. Calibrate without reinstalling: -CalibrateOnly.

param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [Parameter(Mandatory = $true)][int]$Port,
    [int]$PairPort = 0,
    [string]$Code = "",
    [string]$Apk = (Join-Path $PSScriptRoot "ShabbatWatch.apk"),
    [switch]$Calibrate,
    [switch]$CalibrateOnly
)

# Not "Stop": in Windows PowerShell 5.1 adb's normal stderr chatter would abort the script.
$ErrorActionPreference = "Continue"
$Pkg = "com.davidlevi.shabbatwatch"
$Receiver = "$Pkg/.CalibrationReceiver"

function Get-Adb {
    $local = Join-Path $PSScriptRoot "platform-tools\adb.exe"
    if (Test-Path $local) { return $local }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    Write-Host "adb not found - downloading Google's platform-tools..."
    $zip = Join-Path $PSScriptRoot "platform-tools.zip"
    Invoke-WebRequest -ErrorAction Stop "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath $PSScriptRoot -Force
    Remove-Item $zip
    return $local
}

$adb = Get-Adb
$device = "${Ip}:${Port}"
function Adb { & $adb -s $device @args }

if ($PairPort -gt 0 -and $Code) {
    Write-Host "Pairing..."
    & $adb pair "${Ip}:${PairPort}" $Code
}
Write-Host "Connecting..."
& $adb connect $device
if ((& $adb -s $device get-state) -ne "device") {
    throw "Could not connect to the watch at $device. Check the IP/port on the Wireless debugging screen."
}

if (-not $CalibrateOnly) {
    if (-not (Test-Path $Apk)) {
        Write-Host "Downloading the latest APK..."
        Invoke-WebRequest -ErrorAction Stop "https://github.com/Etzel-museum/ShabbatWatch/releases/download/latest/ShabbatWatch.apk" -OutFile $Apk
    }
    Write-Host "Installing..."
    Adb install -r $Apk
    Write-Host "Granting permissions..."
    Adb shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS
    Adb shell pm grant $Pkg android.permission.POST_NOTIFICATIONS
    Adb shell cmd notification allow_dnd $Pkg
    Adb shell appops set $Pkg SYSTEM_ALERT_WINDOW allow
    Adb shell dumpsys deviceidle whitelist +$Pkg
}

if (-not ($Calibrate -or $CalibrateOnly)) {
    Write-Host "Done. You can now turn Wireless debugging off; the permissions stay."
    exit 0
}

# ---- Calibration (optional) --------------------------------------------------
# For settings the app doesn't know yet: we watch which values change while YOU
# flip each switch. Samsung Health's own settings are NOT stored where this can
# see them - on Galaxy watches the app covers those through power saving instead.

function Get-Snapshot {
    $map = @{}
    foreach ($table in "system", "secure", "global") {
        foreach ($line in (Adb shell settings list $table)) {
            $i = $line.IndexOf("=")
            if ($i -gt 0) { $map["$table|" + $line.Substring(0, $i)] = $line.Substring($i + 1) }
        }
    }
    return $map
}

function Get-Changed($a, $b) {
    $keys = @($a.Keys) + @($b.Keys) | Sort-Object -Unique
    return @($keys | Where-Object { $a[$_] -ne $b[$_] })
}

# Label ids are ASCII (Windows PowerShell 5.1 mangles Hebrew); the app shows them in Hebrew.
$items = @(
    @{ Label = "wrist"; Name = "Raise wrist to wake"; Where = "Settings > Display" },
    @{ Label = "tap"; Name = "Touch screen to wake"; Where = "Settings > Display" },
    @{ Label = "palm"; Name = "Cover screen to turn off (palm cover)"; Where = "Settings > Display / Advanced features" }
)

$found = @()
$notFound = @()

# You flip one switch to its Shabbat value and back; a setting counts only if the
# same key(s) change both ways, so random background changes are not picked up.
function Measure-Setting($label, $name, $where) {
    Write-Host ""
    Write-Host "== $name ==   ($where)" -ForegroundColor Cyan
    $answer = Read-Host "Set it to its NORMAL (weekday) value, then press Enter  [or type 'skip']"
    if ($answer -eq "skip") { return }
    $first = Get-Snapshot
    Start-Sleep -Seconds 2
    $before = Get-Snapshot
    $noise = Get-Changed $first $before
    Read-Host "Now set it to its SHABBAT value (off / manual), then press Enter" | Out-Null
    $after = Get-Snapshot
    $changed = @(Get-Changed $before $after | Where-Object { $noise -notcontains $_ -and $after.ContainsKey($_) })
    Read-Host "Now set it back to its NORMAL value, then press Enter" | Out-Null
    $back = Get-Snapshot
    $changed = @($changed | Where-Object { $back[$_] -eq $before[$_] })
    if ($changed.Count -eq 0) {
        Write-Host "  Not found in the watch's system settings - this one stays manual." -ForegroundColor Yellow
        $script:notFound += $name
        return
    }
    foreach ($k in $changed) {
        $table, $key = $k.Split("|", 2)
        Write-Host "  found: $table $key  (normal=$($before[$k]) shabbat=$($after[$k]))" -ForegroundColor Green
        Adb shell am broadcast -a "$Pkg.ADD_TWEAK" -n $Receiver --es label $label --es table $table --es key $key --es off "'$($after[$k])'" | Out-Null
    }
    $script:found += $name
}

Adb shell am broadcast -a "$Pkg.CLEAR_TWEAKS" -n $Receiver | Out-Null

Write-Host ""
Write-Host "CALIBRATION - for each setting you will switch it to the Shabbat value and back."
Write-Host "Keep the watch awake (tap it now and then). Type 'skip' for anything your watch doesn't have."
foreach ($item in $items) { Measure-Setting $item.Label $item.Name $item.Where }

while ($true) {
    Write-Host ""
    $extra = Read-Host "Any OTHER setting to switch off on Shabbat? Type a short English name for it, or press Enter to finish"
    if (-not $extra) { break }
    $label = ($extra -replace '[^A-Za-z0-9 _-]', '').Trim()
    if (-not $label) { $label = "extra" }
    Measure-Setting $label $extra "wherever it is on the watch"
}

Write-Host ""
Write-Host "Handled automatically by the app on Shabbat: $($found -join ', ')" -ForegroundColor Green
if ($notFound.Count) {
    Write-Host "NOT found - switch these by hand before Shabbat: $($notFound -join ', ')" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "Saved in the app:"
Adb shell am broadcast -a "$Pkg.LIST" -n $Receiver
Write-Host ""
Write-Host "Done. You can now turn Wireless debugging off; the permissions stay."
