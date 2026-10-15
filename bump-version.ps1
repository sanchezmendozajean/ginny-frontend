# Sube la versión de la app: versionCode +1 y versionName según el tipo (patch por defecto).
# Actualiza app/build.gradle.kts y los ejemplos de README.md. No hace commit ni compila.
# Uso (desde bump-version.bat): bump-version.bat [patch|minor|major]
param(
    [ValidateSet('patch', 'minor', 'major')][string]$Kind = 'patch',
    [string]$Root = $PSScriptRoot
)
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)

$gradle = Join-Path $Root 'app\build.gradle.kts'
$text = [IO.File]::ReadAllText($gradle, $utf8)

$mName = [regex]::Match($text, 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"')
$mCode = [regex]::Match($text, 'versionCode\s*=\s*(\d+)')
if (-not $mName.Success -or -not $mCode.Success) { throw 'No encontré versionName / versionCode en app/build.gradle.kts.' }

$major = [int]$mName.Groups[1].Value; $minor = [int]$mName.Groups[2].Value; $patch = [int]$mName.Groups[3].Value
$oldName = "$major.$minor.$patch"
switch ($Kind) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    default { $patch++ }
}
$newName = "$major.$minor.$patch"
$oldCode = [int]$mCode.Groups[1].Value
$newCode = $oldCode + 1

$text = $text.Remove($mCode.Index, $mCode.Length).Insert($mCode.Index, "versionCode = $newCode")
$mName = [regex]::Match($text, 'versionName\s*=\s*"[^"]*"')
$text = $text.Remove($mName.Index, $mName.Length).Insert($mName.Index, "versionName = `"$newName`"")
[IO.File]::WriteAllText($gradle, $text, $utf8)

# Ejemplos del README (nombre del APK y bloque defaultConfig).
$readme = Join-Path $Root 'README.md'
if (Test-Path $readme) {
    $r = [IO.File]::ReadAllText($readme, $utf8)
    $r = $r.Replace("GinnyFrontend-$oldName.apk", "GinnyFrontend-$newName.apk").Replace("versionName = `"$oldName`"", "versionName = `"$newName`"")
    [IO.File]::WriteAllText($readme, $r, $utf8)
}

Write-Host "Version: $oldName (code $oldCode) -> $newName (code $newCode)"
Write-Host "Siguiente: compilar con build-release.bat (ver docs\publicar-release.md)."
