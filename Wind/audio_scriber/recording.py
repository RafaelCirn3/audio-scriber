"""Captura áudio de microfone em WAV PCM 16-bit, sem dependência de FFmpeg."""
import queue
import tempfile
import wave
from pathlib import Path

import sounddevice as sd


class AudioRecorder:
    def __init__(self, sample_rate: int = 16000):
        self.sample_rate = sample_rate
        self._stream = None
        self._wave = None
        self._chunks = queue.Queue()
        self.path: Path | None = None
        self.error: str | None = None

    @property
    def is_recording(self) -> bool:
        return self._stream is not None

    def start(self) -> Path:
        if self.is_recording:
            raise RuntimeError("Já existe uma gravação em andamento.")
        self.error = None
        self._chunks = queue.Queue()
        with tempfile.NamedTemporaryFile(prefix="audio_scriber_", suffix=".wav", delete=False) as tmp:
            self.path = Path(tmp.name)
        self._wave = wave.open(str(self.path), "wb")
        self._wave.setnchannels(1)
        self._wave.setsampwidth(2)
        self._wave.setframerate(self.sample_rate)

        def callback(indata, frames, time_info, status):
            if status:
                self.error = str(status)
            self._chunks.put(bytes(indata))

        try:
            self._stream = sd.RawInputStream(
                samplerate=self.sample_rate, channels=1, dtype="int16", callback=callback
            )
            self._stream.start()
        except Exception:
            if self._stream is not None:
                self._stream.close()
                self._stream = None
            self._wave.close()
            self._wave = None
            self.path.unlink(missing_ok=True)
            self.path = None
            raise
        return self.path

    def _flush(self):
        if self._wave is None:
            return
        while True:
            try:
                self._wave.writeframesraw(self._chunks.get_nowait())
            except queue.Empty:
                break

    def poll(self):
        """Consumir periodicamente na thread da GUI, fora do callback de áudio."""
        self._flush()

    def stop(self) -> Path:
        if self._stream is None or self._wave is None or self.path is None:
            raise RuntimeError("Nenhuma gravação em andamento.")
        try:
            self._stream.stop()
        finally:
            self._stream.close()
            self._stream = None
            self._flush()
            self._wave.close()
            self._wave = None
        if self.path.stat().st_size <= 44:
            self.path.unlink(missing_ok=True)
            raise ValueError("Nenhum áudio foi capturado do microfone.")
        return self.path
