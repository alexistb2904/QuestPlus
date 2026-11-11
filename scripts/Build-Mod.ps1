$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

try {
    $propertiesPath = "$root/mod/gradle.properties"
    $properties = [IO.File]::ReadAllText($propertiesPath)
    $currentVersion = ([regex]::Match($properties, '(?m)^mod_version=([^\r\n]+)').Groups[1].Value).Trim()
    $version = (Read-Host "Mod version [$currentVersion] - press Enter to keep").Trim()
    if (-not $version) { $version = $currentVersion }
    if ($version -cnotmatch '^[0-9A-Za-z][0-9A-Za-z._+-]*$') {
        throw 'Invalid version: use only letters, digits, dots, hyphens, + and _.'
    }

    if ($version -cne $currentVersion) {
        $properties = [regex]::Replace($properties, '(?m)^mod_version=[^\r\n]*', "mod_version=$version")
        [IO.File]::WriteAllText($propertiesPath, $properties, [Text.UTF8Encoding]::new($false))
        Write-Host "[OK] Project version updated to $version"
    }

    & "$PSScriptRoot/_gradle.bat" universalJar neoforgeJar
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $jar = "$root/mod/build/libs/questplus-$version.jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "JAR not found: $jar"
    }
    $output = "$root/output"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $destination = Join-Path $output "questplus-$version.jar"
    Copy-Item -LiteralPath $jar -Destination $destination -Force
    Write-Host "[OK] Compiled mod: $destination"
    $neoforge = "$root/mod/build/libs/questplus-$version-neoforge.jar"
    if (Test-Path -LiteralPath $neoforge -PathType Leaf) {
        Copy-Item -LiteralPath $neoforge -Destination (Join-Path $output "questplus-$version-neoforge.jar") -Force
        Write-Host "[OK] Compiled NeoForge mod: $output/questplus-$version-neoforge.jar"
    }
    exit 0
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
