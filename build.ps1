param([switch]$Test, [switch]$UiTest)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw 'JDK 17 or newer is required. Add its bin folder to PATH.'
    }
    New-Item -ItemType Directory -Force 'build/classes' | Out-Null
    $lifeSources = @(Get-ChildItem 'src' -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
    [IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'build/sources.txt'), $lifeSources, [Text.UTF8Encoding]::new($false))
    & javac -encoding UTF-8 --release 17 -d build/classes '@build/sources.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    & jar --create --file build/HolubLife.jar --main-class com.holub.life.Life -C build/classes .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
    if ($Test -or $UiTest) {
        New-Item -ItemType Directory -Force 'build/test-classes' | Out-Null
        $lifeTests = @(Get-ChildItem 'test' -Recurse -Filter '*.java' | ForEach-Object FullName)
        & javac -encoding UTF-8 --release 17 -cp build/classes -d build/test-classes $lifeTests
        if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
        & java -ea '-Djava.awt.headless=true' -cp 'build/classes;build/test-classes' com.holub.life.PatternFeatureTest
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed.' }
        if ($UiTest) {
            & java -ea -cp 'build/classes;build/test-classes' com.holub.life.PatternUiTest
            if ($LASTEXITCODE -ne 0) { throw 'Swing integration tests failed.' }
        }
    }
    Write-Output 'Ready: build/HolubLife.jar'
} finally {
    Pop-Location
}
