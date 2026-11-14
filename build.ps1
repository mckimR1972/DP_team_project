param([switch]$Test, [switch]$UiTest)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw 'JDK 17 or newer is required. Add its bin folder to PATH.'
    }
    $classDirectory = Join-Path $PSScriptRoot 'build/standalone/classes'
    $sourceList = Join-Path $PSScriptRoot 'build/standalone/sources.txt'
    $jarPath = Join-Path $PSScriptRoot 'build/HolubLife.jar'
    New-Item -ItemType Directory -Force -Path $classDirectory | Out-Null
    $lifeSources = @(Get-ChildItem 'src' -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines($sourceList, $lifeSources, [Text.UTF8Encoding]::new($false))
    & javac -encoding UTF-8 --release 17 -d $classDirectory ('@' + $sourceList)
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    & jar --create --file $jarPath --main-class com.holub.life.Life -C $classDirectory .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
    if ($Test -or $UiTest) {
        $testTasks = @('test')
        if ($UiTest) { $testTasks += 'patternUiTest' }
        & (Join-Path $PSScriptRoot 'gradlew.bat') @testTasks '--no-daemon' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
    }
    Write-Output 'Ready: build/HolubLife.jar'
} finally {
    Pop-Location
}
