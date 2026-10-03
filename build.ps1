param([switch]$Package, [string]$Version = '1.2.0', [string]$Output = 'publish\release')
$ErrorActionPreference = 'Stop'
$env:DOTNET_CLI_HOME = Join-Path $PSScriptRoot '..\dotnet-home'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_GENERATE_ASPNET_CERTIFICATE = 'false'
$env:NUGET_PACKAGES = Join-Path $PSScriptRoot '..\nuget'
$sdk = Join-Path $PSScriptRoot '..\toolchain\dotnet\dotnet.exe'
if (!(Test-Path -LiteralPath $sdk)) { $sdk = 'dotnet' }
foreach ($project in @('HolyLois.Core','HolyLois.Tests','HolyLois.App','HolyLois.Publisher')) {
    $path = Join-Path $PSScriptRoot "src\$project\$project.csproj"
    & $sdk restore $path --configfile (Join-Path $PSScriptRoot 'NuGet.Config') --ignore-failed-sources
    if ($LASTEXITCODE) { throw "Restore failed: $project" }
    & $sdk build $path -c Release --no-restore --nologo -p:Version=$Version
    if ($LASTEXITCODE) { throw "Build failed: $project" }
}
& $sdk run --project (Join-Path $PSScriptRoot 'src\HolyLois.Tests') -c Release --no-build
if ($LASTEXITCODE) { throw 'Updater tests failed.' }
if ($Package) {
    & $sdk publish (Join-Path $PSScriptRoot 'src\HolyLois.App') -c Release -r win-x64 --self-contained true -p:Version=$Version -p:HolyLoisPreview=false -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:PublishReadyToRun=false -p:EnableCompressionInSingleFile=true --configfile (Join-Path $PSScriptRoot 'NuGet.Config') --ignore-failed-sources -o (Join-Path $PSScriptRoot $Output)
    if ($LASTEXITCODE) { throw 'Preview packaging failed.' }
}
