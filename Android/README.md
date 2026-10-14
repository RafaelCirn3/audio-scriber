# Audio Scriber Android — instalação direta no tablet

## Cliente: instalar sem computador

1. No tablet Android, abra o link público: https://github.com/RafaelCirn3/audio-scriber/releases/latest (quando houver release estável) ou https://github.com/RafaelCirn3/audio-scriber/releases/tag/v0.1.0-beta para a versão de testes.
2. Em **Assets**, toque em **AudioScriber-Android-beta.apk** (não é preciso baixar ZIP nem abrir GitHub Actions).
3. Abra o download do APK e autorize a instalação desta fonte nas configurações Android, se solicitado.
4. Toque em **Instalar** e abra o ícone Audio Scriber. Conceda acesso ao microfone para gravar.
5. A primeira transcrição baixa o modelo de português; depois o reconhecimento acontece no próprio tablet, mesmo offline.

**Não é necessário PC, navegador durante o uso, nem servidor.** O navegador só é usado para baixar o instalador uma única vez.

**Atenção:** a versão `v0.1.0-beta` é de testes e usa assinatura automática de depuração do GitHub Actions, não uma assinatura permanente de distribuição. Atualizações futuras podem exigir desinstalar antes de instalar outra compilação, perdendo dados locais. Não entregue como versão de produção antes de assinar com uma chave de release própria e validar microfone, formatos, modelo offline e exportação em tablet real.

## Desenvolvimento

Abra este diretório como projeto no Android Studio (JDK 17; SDK 35). A compilação via GitHub Actions gera APK e publica na página Releases. O aplicativo é Kotlin/Android SDK e usa Vosk para reconhecimento de português.

## Limitações

- Máximo de 15 minutos por gravação/importação no MVP.
- Uma transcrição por vez, em primeiro plano.
- Sem diarização e sem modelos adicionais neste momento.
- `server.py`, `static/` e `start_server.bat` são componentes web legados e não são usados pelo aplicativo Android nativo.
