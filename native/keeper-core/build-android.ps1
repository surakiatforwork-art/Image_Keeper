$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..\..").Path
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$ndk = $env:ANDROID_NDK_HOME
if (!$ndk) {
    $ndk = Get-ChildItem (Join-Path $sdk 'ndk') -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe') } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (!$ndk) { throw 'Install Android NDK via SDK Manager' }
$clang = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
$rustc = Join-Path $env:USERPROFILE '.cargo\bin\rustc.exe'
if (!(Test-Path $rustc)) { $rustc = (Get-Command rustc -ErrorAction Stop).Source }
$targets = @(
    @{ abi='arm64-v8a'; rust='aarch64-linux-android'; clang='aarch64-linux-android26' },
    @{ abi='armeabi-v7a'; rust='armv7-linux-androideabi'; clang='armv7a-linux-androideabi26' },
    @{ abi='x86_64'; rust='x86_64-linux-android'; clang='x86_64-linux-android26' },
    @{ abi='x86'; rust='i686-linux-android'; clang='i686-linux-android26' }
)
foreach ($target in $targets) {
    $out = Join-Path $root ("app\src\main\jniLibs\" + $target.abi)
    New-Item -ItemType Directory -Force $out | Out-Null
    $source = Join-Path $PSScriptRoot 'src\lib.rs'
    $lib = Join-Path $out 'libkeeper_core.so'
    Write-Output ("Building " + $target.abi)
    & $rustc $source --edition=2021 --crate-name keeper_core --crate-type cdylib --target $target.rust -C opt-level=3 -C panic=abort -C strip=symbols -C "linker=$clang" -C "link-arg=--target=$($target.clang)" -C "link-arg=-Wl,-z,max-page-size=16384" -o $lib
    if ($LASTEXITCODE -ne 0) { throw "Rust build failed for $($target.abi)" }
    Get-Item $lib | Select-Object Name, Length
}
