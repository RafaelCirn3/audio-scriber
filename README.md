# Audio Scriber

Aplicativo para transcrever áudio em arquivos de texto (.txt), com versões distintas para Windows e Android.

## Android nativo — processamento no próprio tablet

Abra a pasta [Android](Android/) no **Android Studio** (projeto Gradle/Kotlin) e gere o APK. O aplicativo usa **Vosk Android**, não precisa de navegador ou servidor e não envia gravações a APIs externas. Os modelos são instalados no dispositivo durante o primeiro uso (internet necessária apenas para baixar o modelo); o reconhecimento posteriormente funciona offline.

- **Grave um áudio:** captura pelo microfone do Android (permissão solicitada pelo sistema).
- **Envie um áudio:** abre seletor nativo de documentos e decodifica trilhas de áudio compatíveis com MediaCodec.
- **Transcrever áudio:** executa o modelo Vosk no tablet ou celular.
- **Salvar .txt:** usa seletor de documentos Android para salvar o texto, sem permissão de armazenamento ampla.

**Limites MVP:** português, áudios de até 15 minutos, execução em primeiro plano, sem diarização ou pontuação inteligente. O uso de outros modelos/idiomas será uma etapa posterior.

Instruções completas: [Android/README.md](Android/README.md).

## Windows — processamento local

A versão desktop em Tkinter foi preservada na pasta [Wind](Wind/). No PowerShell:

```powershell
cd Wind
py -m pip install -r requirements.txt
py app.py
```

Para criar um executável e um atalho na Área de Trabalho:

```powershell
cd Wind
.\build_windows.bat
```

A versão Windows utiliza faster-whisper e a Android utiliza Vosk, ambas com inferência local.

## Projeto legado de Android via navegador

Arquivos como `Android/server.py`, `Android/static/` e `Android/start_server.bat` pertencem ao protótipo web anterior. **Não são necessários para instalar ou utilizar o aplicativo Android nativo** e foram preservados apenas temporariamente para não descartar trabalho existente.

## Estado de validação

O código Kotlin foi publicado, mas o APK ainda **não foi compilado nem testado em tablet real** nesta sessão. Antes de entregar ao cliente, execute o build Android, teste gravação/importação/exportação, permissões, downloads e uso offline no dispositivo-alvo.
