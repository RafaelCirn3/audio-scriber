# Android (celular/tablet)

Aplicação web responsiva. **Não é APK**. Consulte o [guia principal](../README.md) para executar o servidor e usar pelo Chrome Android.

O servidor é FastAPI + faster-whisper; o áudio é processado no computador/servidor. A interface móvel tem gravação com MediaRecorder (em HTTPS), seleção de áudio e download do texto.

Para teste em Wi-Fi, execute `start_server.bat` no Windows e acesse `http://IP-DO-PC:8765` no celular. Em HTTP pelo IP local, utilize a importação de áudios; a gravação direta exige HTTPS.

**Não exponha o serviço sem autenticação em produção.**
