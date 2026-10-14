@echo off
setlocal
cd /d "%~dp0"

where py >nul 2>nul
if errorlevel 1 (
  echo [ERRO] Python Launcher nao encontrado.
  exit /b 1
)

echo [1/4] Instalando dependencias...
py -m pip install -r requirements.txt pyinstaller pillow
if errorlevel 1 exit /b 1

echo [2/4] Gerando icone para o aplicativo...
py generate_icon.py
if errorlevel 1 exit /b 1

echo [3/4] Compilando executavel Windows...
py -m PyInstaller --noconfirm --clean --windowed --onedir --name AudioScriber --icon "assets\audio_scriber.ico" --collect-all faster_whisper --collect-all ctranslate2 --collect-all av --collect-all sounddevice app.py
if errorlevel 1 (
  echo [ERRO] Compilacao falhou. O atalho nao sera criado.
  exit /b 1
)

set "APP_EXE=%CD%\dist\AudioScriber\AudioScriber.exe"
if not exist "%APP_EXE%" (
  echo [ERRO] O executavel nao foi encontrado em "%APP_EXE%".
  exit /b 1
)

echo [4/4] Criando atalho na Area de Trabalho...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\create_shortcut.ps1" -Executable "%APP_EXE%"
if errorlevel 1 (
  echo [AVISO] Build finalizado, mas nao foi possivel criar o atalho.
  echo Executavel: "%APP_EXE%"
  exit /b 1
)

echo.
echo Audio Scriber compilado com sucesso!
echo Atalho "Audio Scriber" criado na Area de Trabalho.
echo Nao mova a pasta dist\AudioScriber: o atalho aponta para o executavel nela.
exit /b 0
