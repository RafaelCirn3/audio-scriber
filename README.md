# Audio Scriber

Aplicativo desktop para Windows, em Python + Tkinter, que grava áudio pelo microfone ou importa um arquivo de áudio e gera uma transcrição `.txt` com faster-whisper.

## Instalação

No PowerShell (Python 3.11 ou 3.12 recomendado):

```powershell
git clone https://github.com/RafaelCirn3/audio-scriber.git
cd audio-scriber
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python app.py
```

Se o PowerShell bloquear a ativação, execute `\.venv\Scripts\python.exe app.py` e utilize esse Python para instalar os requisitos.

## Utilização

1. Clique em **Grave um áudio** e permita o uso do microfone. Clique em **Parar gravação** quando terminar; ou clique em **Envie um áudio** e selecione um arquivo compatível.
2. Se desejar, ajuste o modelo e o idioma. Clique em **Transcrever áudio**.
3. Ao concluir, visualize o texto e escolha onde salvar o `.txt` na janela do Windows. Se cancelar a janela, poderá clicar em **Salvar .txt** depois.

O download do modelo na primeira utilização exige internet. Depois que o modelo estiver em cache, a inferência é local e não envia os áudios para serviços externos. O tempo de processamento depende do hardware e da duração do arquivo.

Formatos importados: WAV, MP3, M4A, AAC, OGG, OPUS, FLAC, MP4 e MOV. O decodificador PyAV acompanha o faster-whisper; não é necessária instalação independente do FFmpeg para os formatos que ele suportar.

## Modelos

- `tiny`: mais leve; menor precisão.
- `base`: intermediário leve.
- `small` (padrão): equilíbrio entre qualidade e custo.
- `medium`: maior demanda de CPU/memória.

A configuração padrão usa CPU com quantização `int8`, priorizando computadores Windows sem GPU dedicada. O reconhecimento automático de idioma é o padrão; também há português, inglês e espanhol.

## Compilação para Windows

No Windows, execute `build_windows.bat`. O script instala as dependências, gera `assets\audio_scriber.ico`, compila `dist\AudioScriber\AudioScriber.exe` com o ícone embutido e cria automaticamente o atalho **Audio Scriber** na Área de Trabalho do usuário (somente após a compilação terminar com sucesso). O atalho aponta para o executável na pasta `dist`: não mova ou exclua essa pasta. Em caso de falha ao criar o atalho, o script informa o caminho do executável. A primeira transcrição ainda baixa o modelo escolhido; é necessário acesso à internet no primeiro uso.

**Nota:** build e teste do executável precisam ser realizados em Windows. A publicação do código-fonte não significa que um instalador ou `.exe` já foi compilado e validado.

## Estrutura

- `app.py`: interface e fluxo de gravação/transcrição.
- `audio_scriber/transcription.py`: integração com Whisper, independente da GUI.
- `audio_scriber/recording.py`: captura WAV pelo microfone.
- `requirements.txt`: bibliotecas necessárias.
- `build_windows.bat`: empacotamento nativo e criação do atalho na Área de Trabalho.\n- `generate_icon.py`: gera o ícone Windows `.ico` em múltiplas resoluções.\n- `scripts/create_shortcut.ps1`: cria um atalho Windows `.lnk` com ícone do executável.
- `tests/`: testes automatizados das operações sem precisar baixar modelos.

## Limitações do MVP

- Uma transcrição por vez, sem diarização de locutores.
- Os modelos são baixados sob demanda; não estão versionados no GitHub.
- A interface é local. Não se trata de cópia de código, APIs privadas ou marca do TurboScribe.
- A caixa de diálogo **Salvar como** representa o 'download' para o disco no contexto de uma aplicação nativa.

## Referências

- [TurboScribe — funcionalidades públicas](https://turboscribe.com/)
- [faster-whisper — implementação open source](https://github.com/SYSTRAN/faster-whisper)
