param(
    [Parameter(Mandatory=$true)]
    [string]$Executable
)
$ErrorActionPreference = "Stop"
$exePath = [System.IO.Path]::GetFullPath($Executable)
if (-not (Test-Path -LiteralPath $exePath -PathType Leaf)) {
    throw "Executavel nao encontrado: $exePath"
}
$desktop = [Environment]::GetFolderPath("DesktopDirectory")
if ([string]::IsNullOrWhiteSpace($desktop)) {
    throw "Pasta da Area de Trabalho nao encontrada."
}
$shortcutPath = Join-Path $desktop "Audio Scriber.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exePath
$shortcut.WorkingDirectory = Split-Path -Parent $exePath
$shortcut.IconLocation = "$exePath,0"
$shortcut.Description = "Audio Scriber - transcricao de audio em texto"
$shortcut.Save()
Write-Host "Atalho criado: $shortcutPath"
