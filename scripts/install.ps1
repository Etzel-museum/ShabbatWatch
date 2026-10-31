# Installs "Shabbat mode" on a Galaxy Watch over Wi-Fi, grants its permissions,
# and calibrates which watch settings to switch off during Shabbat.
#
# On the watch: Settings > About watch > Software > tap "Software version" 7 times,
# then Settings > Developer options > ADB debugging ON + Wireless debugging ON.
# Wireless debugging > "Pair new device" shows IP, pairing port and a 6-digit code;
# the main Wireless debugging screen shows the (different) connection port.
#
#   powershell -ExecutionPolicy Bypass -File install.ps1 -Ip 192.168.1.50 -Port 41234 -PairPort 37000 -Code 123456
#
# Already paired before? Leave out -PairPort/-Code. Only re-run calibration: add -CalibrateOnly.

param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [Parameter(Mandatory = $true)][int]$Port,
    [int]$PairPort = 0,
    [string]$Code = "",
    [string]$Apk = (Join-Path $PSScriptRoot "ShabbatWatch.apk"),
    [switch]$CalibrateOnly,
    [switch]$SkipCalibration
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

if ($SkipCalibration) { Write-Host "Done."; exit 0 }

# ---- Calibration -------------------------------------------------------------
# Samsung's setting names differ between watch models and software versions, so
# instead of guessing we watch which values change while YOU flip each switch.

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

$items = @(
    @{ Label = "wrist"; Name = "Raise wrist to wake" },
    @{ Label = "tap"; Name = "Touch screen to wake" },
    @{ Label = "palm"; Name = "Cover screen to turn off (palm cover)" }
)

Adb shell am broadcast -a "$Pkg.CLEAR_TWEAKS" -n $Receiver | Out-Null
$restore = @()

Write-Host ""
Write-Host "CALIBRATION - keep the watch awake (tap it now and then) and use its Settings app."
Write-Host "Most of these are under Settings > Display (or Settings > Advanced features)."
foreach ($item in $items) {
    Write-Host ""
    Write-Host "== $($item.Name) =="
    $answer = Read-Host "Make sure it is ON on the watch, then press Enter (type 'skip' if the watch has no such setting)"
    if ($answer -eq "skip") { continue }
    $first = Get-Snapshot
    Start-Sleep -Seconds 2
    $before = Get-Snapshot
    $noise = Get-Changed $first $before
    Read-Host "Now turn it OFF on the watch, then press Enter" | Out-Null
    $after = Get-Snapshot
    $changed = @(Get-Changed $before $after | Where-Object { $noise -notcontains $_ -and $after.ContainsKey($_) })
    if ($changed.Count -eq 0) {
        Write-Host "No change detected - skipping this one." -ForegroundColor Yellow
        continue
    }
    foreach ($k in $changed) {
        $table, $key = $k.Split("|", 2)
        Write-Host "  found: $table $key  (on=$($before[$k]) off=$($after[$k]))" -ForegroundColor Green
        Adb shell am broadcast -a "$Pkg.ADD_TWEAK" -n $Receiver --es label $item.Label --es table $table --es key $key --es off "'$($after[$k])'" | Out-Null
        if ($before.ContainsKey($k)) { $restore += , @($table, $key, $before[$k]) }
    }
}

# Put the watch back the way it was; the app switches these off only during Shabbat.
foreach ($r in $restore) { Adb shell settings put $r[0] $r[1] "'$($r[2])'" | Out-Null }

Write-Host ""
Write-Host "Saved in the app:"
Adb shell am broadcast -a "$Pkg.LIST" -n $Receiver
Write-Host ""
Write-Host "Done. Check on the watch that the settings you turned off are back ON."
Write-Host "You can now turn Wireless debugging off; the permissions stay."
