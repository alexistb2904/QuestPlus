param(
    [ValidateRange(1, 5)][int]$Repetitions = 3,
    [switch]$CompareParent,
    [switch]$RecordJfr
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $projectRoot
try {
    $jdkHome = & "$projectRoot/scripts/Find-Java25.ps1"
    if (!$jdkHome) { throw 'JDK 25 not found. Set JAVA_HOME or QUESTPLUS_JAVA_HOME to a JDK 25 directory.' }
    $env:JAVA_HOME = $jdkHome.Trim()
    $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    $javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
    & ./mod/gradlew.bat -p mod -I ../scripts/benchmark/benchmark.gradle prepareBenchmark --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark compilation failed' }
    $classpath = Get-Content mod/build/benchmark/classpath.txt -Raw
    $metadata = [ordered]@{
        current = (& git rev-parse HEAD); baseline = (& git rev-parse 'HEAD^1'); date = (Get-Date -Format o)
        cpu = (Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors)
        memory = (Get-CimInstance Win32_OperatingSystem | Select-Object TotalVisibleMemorySize, FreePhysicalMemory)
        repetitions = $Repetitions
    }
    $metadata | ConvertTo-Json -Depth 4 | Set-Content mod/build/benchmark/environment.json -Encoding utf8
    if ($CompareParent) {
        foreach ($relative in @('core/QuestEngine', 'core/QuestRegistry', 'core/PlayerProgress', 'persistence/JsonProgressStore', 'clientdata/ClientProjection')) {
            $destination = "mod/build/benchmark/baseline-src/$relative.java"
            New-Item -ItemType Directory -Force (Split-Path $destination -Parent) | Out-Null
            $source = & git show "HEAD^1:mod/server/src/main/java/dev/questplus/$relative.java"
            if ($LASTEXITCODE -ne 0) { throw "Cannot read baseline $relative" }
            [IO.File]::WriteAllLines((Join-Path $projectRoot $destination), $source, [Text.UTF8Encoding]::new($false))
        }
        $sources = @(Get-ChildItem mod/build/benchmark/baseline-src -Filter '*.java' -Recurse | ForEach-Object FullName)
        & $javac -encoding UTF-8 -cp $classpath -d mod/build/benchmark/baseline-classes @sources
        if ($LASTEXITCODE -ne 0) { throw 'Baseline overlay compilation failed' }
    }
    for ($repeat = 1; $repeat -le $Repetitions; $repeat++) {
        $modes = if ($CompareParent) { if ($repeat % 2) { @('current', 'baseline') }else { @('baseline', 'current') } }else { @('current') }
        foreach ($mode in $modes) {
            $runClasspath = if ($mode -eq 'baseline') { (Join-Path $projectRoot 'mod/build/benchmark/baseline-classes') + [IO.Path]::PathSeparator + $classpath }else { $classpath }
            & $java -ea -Xms512m -Xmx3g "-Xlog:gc:file=mod/build/benchmark/$mode-$repeat-gc.log" -cp $runClasspath dev.questplus.benchmark.QuestPlusBenchmark "mod/build/benchmark/$mode-$repeat" $mode |
            Tee-Object -FilePath "mod/build/benchmark/$mode-$repeat.log"
            if ($LASTEXITCODE -ne 0) { throw "Benchmark $mode-$repeat failed; partial results may exist" }
            $result = Get-Content "mod/build/benchmark/$mode-$repeat/results.json" -Raw | ConvertFrom-Json
            if ($result.persistence_failures) { Write-Warning "$mode-$repeat recorded $($result.persistence_failures) persistence failures; failed trials are not timing results." }
            & $java -ea -Xms512m -Xmx3g -cp $runClasspath dev.questplus.benchmark.QuestPlusBenchmark "mod/build/benchmark/$mode-conditions-$repeat" $mode conditions |
            Tee-Object -FilePath "mod/build/benchmark/$mode-conditions-$repeat.log"
            if ($LASTEXITCODE -ne 0) { throw "Prerequisite benchmark $mode-$repeat failed" }
        }
    }
    if ($RecordJfr) {
        & $java -ea -Xms512m -Xmx3g '-XX:StartFlightRecording=settings=profile,dumponexit=true,maxsize=128m,filename=mod/build/benchmark/components.jfr' -cp $classpath dev.questplus.benchmark.QuestPlusBenchmark mod/build/benchmark/profile current |
        Tee-Object -FilePath mod/build/benchmark/profile.log
        if ($LASTEXITCODE -ne 0) { throw 'JFR benchmark failed' }
    }
}
finally { Pop-Location }
