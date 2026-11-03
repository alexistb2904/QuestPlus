# Windows PowerShell 5.1-compatible lookup. Print ONLY the JDK home to stdout.
# A JDK is required because the Gradle plugin itself runs on Java 25.
$ErrorActionPreference = 'SilentlyContinue'
$candidates = New-Object 'System.Collections.Generic.List[string]'
$seen = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)

function Add-Candidate([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return }
    try {
        $normalized = [System.IO.Path]::GetFullPath($path.Trim('"'))
        if ($seen.Add($normalized)) { $candidates.Add($normalized) }
    } catch { }
}

# An explicit project-local override comes before the global Java installation.
Add-Candidate $env:QUESTPLUS_JAVA_HOME
Add-Candidate $env:JAVA_HOME

# Check JDKs already present in PATH without relying on the default java.exe.
foreach ($part in ($env:PATH -split ';')) {
    if ([string]::IsNullOrWhiteSpace($part)) { continue }
    try {
        if ((Split-Path -Leaf $part.TrimEnd('\')) -ieq 'bin') {
            Add-Candidate (Split-Path -Parent $part.TrimEnd('\'))
        }
    } catch { }
}

$pf = [Environment]::GetFolderPath('ProgramFiles')
$pf86 = [Environment]::GetFolderPath('ProgramFilesX86')
$homeDir = $env:USERPROFILE
$local = $env:LOCALAPPDATA

$locations = @(
    "$pf\Eclipse Adoptium",
    "$pf\Microsoft",
    "$pf\Java",
    "$pf\Amazon Corretto",
    "$pf\Azul Systems",
    "$pf\BellSoft",
    "$pf\RedHat",
    "$pf\OpenJDK",
    "$pf86\Eclipse Adoptium",
    "$homeDir\.jdks",
    "$homeDir\scoop\apps",
    "$local\Programs\Eclipse Adoptium",
    "$local\Programs\Microsoft",
    "$local\Microsoft\WinGet\Packages"
)

foreach ($location in $locations) {
    if (!(Test-Path -LiteralPath $location -PathType Container)) { continue }
    Add-Candidate $location
    foreach ($child in (Get-ChildItem -LiteralPath $location -Directory -ErrorAction SilentlyContinue)) {
        Add-Candidate $child.FullName
        # Handles scoop/current, winget packages and nested vendor directories.
        foreach ($nested in (Get-ChildItem -LiteralPath $child.FullName -Directory -ErrorAction SilentlyContinue)) {
            Add-Candidate $nested.FullName
        }
    }
}

foreach ($candidate in $candidates) {
    $javac = Join-Path $candidate 'bin\javac.exe'
    $java = Join-Path $candidate 'bin\java.exe'
    if (!(Test-Path -LiteralPath $javac -PathType Leaf) -or !(Test-Path -LiteralPath $java -PathType Leaf)) { continue }
    try {
        $version = ((& $javac '-version') | Out-String).Trim()
        if ($version -match '^javac\s+25(?:[.\s+\-]|$)') {
            Write-Output $candidate
            exit 0
        }
    } catch { }
}

exit 1
