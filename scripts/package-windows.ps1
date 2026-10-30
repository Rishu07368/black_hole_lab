param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $jpackage = Get-Command jpackage -ErrorAction Stop
    $jlink = Get-Command jlink -ErrorAction Stop
    $mavenArgs = @('clean', 'package')
    if ($SkipTests) {
        $mavenArgs += '-DskipTests'
    }

    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE."
    }

    $version = '0.1.0'
    [xml]$pom = Get-Content -Raw (Join-Path $root 'pom.xml')
    $version = [string]$pom.project.version
    $inputDirectory = Join-Path $root 'target\package-input'
    $distDirectory = Join-Path $root 'dist'
    $runtimeDirectory = Join-Path $root 'target\package-runtime'
    $appImage = Join-Path $distDirectory 'Gargantua'
    $zipPath = Join-Path $distDirectory "Gargantua-$version-windows-x64.zip"
    $mainJar = "gargantua-$version.jar"

    New-Item -ItemType Directory -Force -Path $inputDirectory, $distDirectory | Out-Null
    Copy-Item (Join-Path $root "target\$mainJar") $inputDirectory -Force
    & mvn -q dependency:copy-dependencies "-DoutputDirectory=$inputDirectory" -DincludeScope=runtime
    if ($LASTEXITCODE -ne 0) {
        throw "Copying runtime dependencies failed with exit code $LASTEXITCODE."
    }
    Get-ChildItem -LiteralPath $inputDirectory -Filter '*-natives-*.jar' |
        Where-Object { $_.Name -notmatch '-natives-windows-amd64\.jar$' } |
        Remove-Item -Force

    if (Test-Path -LiteralPath $appImage) {
        Remove-Item -LiteralPath $appImage -Recurse -Force
    }
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    if (Test-Path -LiteralPath $runtimeDirectory) {
        Remove-Item -LiteralPath $runtimeDirectory -Recurse -Force
    }

    & $jlink.Source --add-modules java.se,jdk.unsupported `
        --strip-debug --no-header-files --no-man-pages --output $runtimeDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "jlink failed with exit code $LASTEXITCODE."
    }

    & $jpackage.Source --type app-image --input $inputDirectory --dest $distDirectory `
        --name Gargantua --main-jar $mainJar `
        --main-class com.blackholelab.app.GargantuaApplication `
        --app-version $version --vendor 'Gargantua Black Hole Lab' `
        --runtime-image $runtimeDirectory `
        --java-options '--enable-native-access=ALL-UNNAMED'
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $launcher = Join-Path $appImage 'Gargantua.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaging completed but the expected launcher was not created: $launcher"
    }

    Compress-Archive -Path $appImage -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Output 'Windows application image created:'
    Get-Item $launcher, $zipPath | Select-Object FullName, Length
    Write-Output "Open $launcher to start Gargantua."
}
finally {
    Pop-Location
}
