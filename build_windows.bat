@echo off
setlocal
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (echo Python launcher nao encontrado. & exit /b 1)
py -m pip install -r requirements.txt pyinstaller
if errorlevel 1 exit /b 1
py -m PyInstaller --noconfirm --clean --windowed --onedir --name AudioScriber --collect-all faster_whisper --collect-all ctranslate2 --collect-all av --collect-all sounddevice app.py
if errorlevel 1 exit /b 1
echo Executavel criado em dist\AudioScriber\AudioScriber.exe
