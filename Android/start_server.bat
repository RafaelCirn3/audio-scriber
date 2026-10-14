@echo off
setlocal
cd /d "%~dp0\.."
where py >nul 2>nul
if errorlevel 1 (echo Python nao encontrado. & pause & exit /b 1)
echo Instalando dependencias do servidor...
py -m pip install -r Android\requirements.txt
if errorlevel 1 (pause & exit /b 1)
echo.
echo Use o IP local do computador no navegador Android:
ipconfig | findstr /i "IPv4"
echo.
echo Porta 8765. Celular/tablet e PC devem estar na mesma rede Wi-Fi.
echo ATENCAO: nao exponha esta porta diretamente a internet. HTTP nao permite gravacao pelo navegador Android.
echo Para usar o microfone do navegador, hospede sob HTTPS com autenticacao.
py -m uvicorn Android.server:app --host 0.0.0.0 --port 8765
pause
