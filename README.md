# Audio Scriber

Transcrição de áudio para texto com Whisper. O repositório tem duas interfaces independentes, com **transcrição processada em CPU**:

- **[Wind/](Wind/)**: aplicativo desktop Tkinter no Windows, com captura do microfone, importação de áudio, exportação .txt, build PyInstaller e atalho na Área de Trabalho.
- **[Android/](Android/)**: aplicação web responsiva para celulares e tablets Android (PWA experimental), com gravação pelo navegador quando hospedada em HTTPS, importação de áudio e download .txt. **Não é um APK** e requer computador/servidor para processar a transcrição.

## Windows desktop

No PowerShell, na raiz do repositório:

```powershell
cd Wind
py -m pip install -r requirements.txt
py app.py
```

Para compilar o executável e criar o atalho na Área de Trabalho:

```powershell
cd Wind
.\build_windows.bat
```

O executável ficará em `Wind\dist\AudioScriber\AudioScriber.exe`. Não mova a pasta `dist\AudioScriber` após gerar o atalho. A compilação precisa ser realizada no Windows.

## Android: primeiro teste na rede Wi-Fi

1. Deixe computador e Android na **mesma rede Wi-Fi**, em uma rede confiável.
2. No Windows, na raiz do repositório, execute `Android\start_server.bat`. Ele instala as dependências e inicia o serviço na porta 8765.
3. Verifique o **IPv4 do computador** exibido pelo script (ex.: `192.168.1.20`).
4. No Chrome do Android, abra `http://192.168.1.20:8765` (substitua pelo seu IP).
5. Toque em **Envie um áudio** e escolha uma gravação do celular. Toque em **Transcrever áudio** e depois em **Baixar .txt**.

**Atenção ao microfone:** o navegador Android exige HTTPS (contexto seguro) para `getUserMedia`. Por isso, em acesso HTTP ao IP local, a gravação direta pelo botão **Grave um áudio** fica indisponível. Grave pelo gravador do telefone e use **Envie um áudio**. Para liberar a gravação diretamente no navegador, implante a aplicação atrás de um endpoint **HTTPS** com certificado válido e autenticação. A UI já implementa a captura via MediaRecorder nos navegadores compatíveis.

**Instalação na tela inicial:** no Chrome Android, abra a versão hospedada em HTTPS e use o menu ⋮ → **Instalar app** ou **Adicionar à tela inicial** (a oferta de instalação varia conforme o navegador e a compatibilidade do manifesto).

**Segurança e privacidade:** esta primeira versão não tem login. O script abre o serviço para a rede local e qualquer dispositivo que alcance a porta poderá enviar áudios para processamento. **Não publique a porta 8765 diretamente na internet**, nem use em rede não confiável. Para uso real com clientes, adicione autenticação, HTTPS, limites por usuário, observabilidade e política de retenção. O servidor recebe temporariamente o arquivo enviado, faz a inferência localmente e remove o arquivo temporário ao concluir; os textos retornam ao navegador. A rede HTTP local não é criptografada.

O arquivo aceito tem até 75 MB. Transcrições são processadas uma de cada vez para proteger a memória da máquina. O modelo é baixado na primeira utilização (requer internet).

## Android: iniciar manualmente

A partir da raiz do repositório:

```powershell
py -m pip install -r Android\requirements.txt
py -m uvicorn Android.server:app --host 0.0.0.0 --port 8765
```

O navegador Android é apenas a interface: é necessário manter o servidor ligado. Não há processamento on-device nem aplicativo APK nesta etapa.

## Estrutura

```text
audio-scriber/
├── Wind/
│   ├── app.py
│   ├── audio_scriber/
│   ├── build_windows.bat
│   ├── generate_icon.py
│   ├── scripts/create_shortcut.ps1
│   └── tests/
└── Android/
    ├── server.py
    ├── start_server.bat
    ├── requirements.txt
    └── static/
        ├── index.html
        ├── style.css
        ├── app.js
        ├── manifest.webmanifest
        └── icon.svg
```

## Limitações

- MVP ainda sem testes em dispositivos Android físicos ou build Windows verificado.
- Android via browser, sem APK; instalação tipo PWA depende do navegador/HTTPS.
- Não há diarização de locutores nem contas de usuário.
- Modelo `small` padrão; desempenho varia conforme CPU, duração e áudio.
- Implementação independente do código proprietário do TurboScribe.
