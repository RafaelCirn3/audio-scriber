"""Serviço de transcrição desacoplado da interface gráfica."""
from pathlib import Path

SUPPORTED_EXTENSIONS = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".mp4", ".mov"}
MODELS = ("tiny", "base", "small", "medium")
LANGUAGES = {"Automático": None, "Português": "pt", "English": "en", "Español": "es"}


def validate_audio(path: str | Path) -> Path:
    audio = Path(path).expanduser()
    if not audio.is_file():
        raise FileNotFoundError("O arquivo de áudio não existe.")
    if audio.suffix.lower() not in SUPPORTED_EXTENSIONS:
        raise ValueError("Formato não suportado. Use WAV, MP3, M4A, AAC, OGG, OPUS, FLAC, MP4 ou MOV.")
    if audio.stat().st_size == 0:
        raise ValueError("O arquivo está vazio.")
    return audio


def transcribe(path: str | Path, model_name: str = "small", language: str | None = None) -> str:
    """Executa Whisper localmente. O modelo é baixado na primeira execução."""
    audio = validate_audio(path)
    if model_name not in MODELS:
        raise ValueError("Modelo Whisper inválido.")
    if language not in (None, "pt", "en", "es"):
        raise ValueError("Idioma inválido.")
    from faster_whisper import WhisperModel  # import tardio evita custo no início da GUI

    model = WhisperModel(model_name, device="cpu", compute_type="int8")
    segments, _info = model.transcribe(
        str(audio), language=language, vad_filter=True, beam_size=5
    )
    text = " ".join(segment.text.strip() for segment in segments if segment.text.strip())
    if not text.strip():
        raise ValueError("Nenhuma fala foi identificada no áudio.")
    return text.strip() + "\n"
