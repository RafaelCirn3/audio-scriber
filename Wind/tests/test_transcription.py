import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch, MagicMock

from audio_scriber.transcription import validate_audio, transcribe


class TestTranscription(unittest.TestCase):
    def test_rejects_missing_file(self):
        with self.assertRaises(FileNotFoundError):
            validate_audio("arquivo-inexistente.wav")

    def test_rejects_extension(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "a.exe"
            path.write_bytes(b"test")
            with self.assertRaises(ValueError):
                validate_audio(path)

    def test_rejects_empty_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "a.wav"
            path.touch()
            with self.assertRaises(ValueError):
                validate_audio(path)

    def test_invalid_model(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "a.wav"
            path.write_bytes(b"1234")
            with self.assertRaises(ValueError):
                transcribe(path, model_name="invalid")


if __name__ == "__main__":
    unittest.main()
