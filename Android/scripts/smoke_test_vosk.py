#!/usr/bin/env python3
"""Fail the Android release if the bundled model cannot be opened by native Vosk."""
import json
import sys
import tempfile
import wave
import zipfile
from pathlib import Path
from vosk import Model, KaldiRecognizer, SetLogLevel

SetLogLevel(-1)
archive_path = Path(sys.argv[1])
with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    with zipfile.ZipFile(archive_path) as archive:
        for member in archive.infolist():
            target = (root / member.filename).resolve()
            if not target.is_relative_to(root.resolve()):
                raise ValueError("Unsafe model ZIP entry")
        archive.extractall(root)
    model_path = root / "vosk-model-small-pt-0.3"
    model = Model(str(model_path))
    recognizer = KaldiRecognizer(model, 16000)
    recognizer.AcceptWaveform(bytes(32000))
    result = json.loads(recognizer.FinalResult())
    assert isinstance(result.get("text"), str), result
    print("Vosk native model load and 16 kHz recognition smoke test: OK")
