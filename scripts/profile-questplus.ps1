#requires -Version 5.1
<#
.SYNOPSIS
    Records a bounded Java Flight Recorder trace for a running QuestPlus JVM.
.EXAMPLE
    .\scripts\profile-questplus.ps1 -ProcessId 12345 -DurationSeconds 120 -Output "profiles/questplus.jfr"
.NOTES
    Run under the same OS account as the target Minecraft process.
    Java 25 JDK jcmd must be present on PATH. Does not restart the server.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$ProcessId,
    [ValidateRange(15,3600)][int]$DurationSeconds=120,
    [ValidateSet("default","profile")][string]$Settings="profile",
    [string]$Output="profiles/questplus.jfr"
)

$ErrorActionPreference="Stop"
$jcmd=Get-Command jcmd -ErrorAction Stop
$absolute=[System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Output))
$directory=[System.IO.Path]::GetDirectoryName($absolute)
[System.IO.Directory]::CreateDirectory($directory) | Out-Null

Write-Host "Recording process $ProcessId for $DurationSeconds seconds; mode=$Settings"
& $jcmd.Source $ProcessId JFR.start "name=QuestPlusProfile" "settings=$Settings" "duration=${DurationSeconds}s" "filename=$absolute"
if($LASTEXITCODE -ne 0){throw "jcmd JFR.start failed (exit $LASTEXITCODE). Check JDK version and process permissions."}
Write-Host "JFR capture scheduled at $absolute. After $DurationSeconds seconds, open it in Java Mission Control."
