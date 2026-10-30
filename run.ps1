param(
    [switch]$GraphicsSmokeTest
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

mvn -q dependency:build-classpath "-Dmdep.outputFile=target\runtime-classpath.txt" -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$java = Get-Command java -ErrorAction Stop
$runtimeClasspath = (Resolve-Path 'target\classes').Path + ';' + (Get-Content 'target\runtime-classpath.txt' -Raw).Trim()
$javaVersion = (Get-Item $java.Source).VersionInfo.ProductVersion
$javaArguments = @()
if ($javaVersion -match '^(\d+)') {
    if ([int]$matches[1] -ge 24) {
        $javaArguments += '--enable-native-access=ALL-UNNAMED'
    }
}
$javaArguments += @('-cp', $runtimeClasspath, 'com.blackholelab.app.GargantuaApplication')
$applicationArguments = @()
if ($GraphicsSmokeTest) {
    $applicationArguments += '--graphics-smoke-test'
}

& $java.Source @javaArguments @applicationArguments
exit $LASTEXITCODE
