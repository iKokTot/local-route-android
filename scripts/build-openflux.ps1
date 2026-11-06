param(
 [Parameter(Mandatory=$true)][string]$Core,
 [Parameter(Mandatory=$true)][string]$Go,
 [Parameter(Mandatory=$true)][string]$Ndk
)
$ErrorActionPreference='Stop'
$Core=(Resolve-Path -LiteralPath $Core).Path
$Go=(Resolve-Path -LiteralPath $Go).Path
$Ndk=(Resolve-Path -LiteralPath $Ndk).Path
$revision = git -C $Core rev-parse HEAD
if ($revision -ne 'f492a1a33b7581ad8f88118f97e0b866adda3cdc') { throw 'Unexpected OpenFlux source revision' }
$repo=Split-Path $PSScriptRoot
$destination=Join-Path $Core 'mobile/cmd/localroute'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot/openflux-helper/main.go" -Destination "$destination/main.go"
Push-Location "$Core/mobile"
try {
 $env:GOOS='android'; $env:CGO_ENABLED='1'
 foreach ($target in @(@('arm64-v8a','arm64','aarch64-linux-android26-clang.cmd'),@('armeabi-v7a','arm','armv7a-linux-androideabi26-clang.cmd'),@('x86_64','amd64','x86_64-linux-android26-clang.cmd'))) {
  $env:GOARCH=$target[1]; $env:GOARM='7'
  $clang=Join-Path $Ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
  $triple=$target[2].Replace('-clang.cmd','')
  $env:CC="$clang --target=$triple"
  $output=Join-Path $repo "app/build/generated/openfluxJniLibs/$($target[0])/libopenflux.so"
  New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null
  & $Go build -buildvcs=false -trimpath -buildmode=pie '-ldflags=-s -w -checklinkname=0' -o $output ./cmd/localroute
  if ($LASTEXITCODE -ne 0) { throw "OpenFlux build failed: $($target[0])" }
 }
 $fixture=Join-Path $Core 'mobile/cmd/localroute-fixture'
 New-Item -ItemType Directory -Force -Path $fixture | Out-Null
 Copy-Item -LiteralPath "$PSScriptRoot/openflux-fixture/main.go" -Destination "$fixture/main.go"
 $env:GOARCH='amd64'; $env:CC="$clang --target=x86_64-linux-android26"
 $output=Join-Path $repo 'app/build/generated/openfluxTestJniLibs/x86_64/libopenflux_fixture.so'
 New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null
 & $Go build -buildvcs=false -trimpath -buildmode=pie '-ldflags=-s -w -checklinkname=0' -o $output ./cmd/localroute-fixture
 if($LASTEXITCODE -ne 0) { throw 'Fixture build failed' }
 Set-Content -LiteralPath (Join-Path $repo 'app/build/generated/openfluxJniLibs/revision.txt') -Value $revision -Encoding ascii
} finally {
 Pop-Location
 'GOOS','GOARCH','GOARM','CGO_ENABLED','CC' | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
}
