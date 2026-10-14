# Audio Scriber para Android — aplicativo nativo Kotlin

Este diretório contém um **projeto Android Studio**, sem necessidade de Chrome, FastAPI, Python, computador ligado ou servidor. A biblioteca [Vosk Android](https://central.sonatype.com/artifact/com.alphacephei/vosk-android/0.3.47) executa reconhecimento de fala **no dispositivo**.

## Como compilar e instalar no tablet

1. Instale o Android Studio com SDK Android 35 e JDK 17.
2. Abra **Open > selecione a pasta `Android/`**.
3. Aguarde a sincronização Gradle com conexão à internet.
4. Conecte o tablet Android por USB e ative a depuração USB, ou gere o APK no Android Studio com **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Instale o APK no dispositivo. O arquivo de depuração geralmente fica em `Android/app/build/outputs/apk/debug/app-debug.apk`.
6. Abra **Audio Scriber**. Toque em **Grave um áudio** (permissão de microfone) ou **Envie um áudio** (seletor de arquivos).
7. Toque em **Transcrever áudio**. Na primeira vez, o aplicativo baixará automaticamente o modelo português (aproximadamente 31 MB) e o guardará em armazenamento privado do aplicativo.
8. Após a transcrição, toque em **Salvar .txt**, escolha a pasta/nome no Android e confirme.

O modelo permanece instalado e permite transcrição **sem internet** nas utilizações seguintes. O áudio e o texto permanecem no dispositivo, sem upload a serviços externos.

## Tecnologias

- Kotlin / Android SDK 35, Android 8.0+ (API 26)
- `AudioRecord` captura PCM mono 16kHz
- `ACTION_OPEN_DOCUMENT` e `MediaExtractor`/`MediaCodec` importam e decodificam arquivos de áudio compatíveis com o dispositivo
- Conversão de áudio PCM para mono 16kHz e reconhecimento via Vosk
- `ACTION_CREATE_DOCUMENT` exporta TXT UTF-8
- Modelo: `vosk-model-small-pt-0.3`, baixado de https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip, com verificação SHA-256 antes da extração

## Restrições

- Limite atual de 15 minutos por áudio. Áudios longos consomem bastante memória; recomenda-se otimização em streaming para produção.
- Apenas português neste MVP.
- O aplicativo precisa permanecer aberto durante a gravação e a transcrição. Uma implementação com serviço de primeiro plano será necessária para transcrições em background.
- Sem diarização e sem pontuação refinada. Vosk não é Whisper; qualidade e tempo de processamento variam.
- APK ainda não validado em um dispositivo real. É necessário testar comportamento de permissões, compatibilidade dos codecs e instalação do modelo.

## Arquivos legados

`server.py`, `static/` e `start_server.bat` são remanescentes do protótipo via navegador; **não são utilizados** pela aplicação Kotlin/Gradle.

## Build de linha de comando

Com Android SDK e Gradle adequadamente instalados, abra `Android/` e execute `gradle assembleDebug`. O projeto não possui wrapper Gradle versionado; a via recomendada inicialmente é o Android Studio.
