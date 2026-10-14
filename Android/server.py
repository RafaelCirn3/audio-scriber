"""Servidor Android (interface web responsiva); transcrição executada no computador/servidor."""
from __future__ import annotations

import asyncio
import os
import tempfile
from pathlib import Path
from threading import BoundedSemaphore

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.concurrency import run_in_threadpool

from Wind.audio_scriber.transcription import MODELS, SUPPORTED_EXTENSIONS, transcribe

HERE = Path(__file__).resolve().parent
MAX_BYTES = 75 * 1024 * 1024
# Uma transcrição de cada vez para limitar o consumo de memória.
slot = BoundedSemaphore(1)
app = FastAPI(title="Audio Scriber Android", docs_url=None, redoc_url=None)
app.mount("/static", StaticFiles(directory=HERE / "static"), name="static")


@app.get("/")
def index():
    return FileResponse(HERE / "static" / "index.html", headers={"Cache-Control": "no-store"})


@app.get("/manifest.webmanifest")
def manifest():
    return FileResponse(HERE / "static" / "manifest.webmanifest", media_type="application/manifest+json")


@app.get("/health")
def health():
    return {"status": "ok"}


def _process(path: str, model: str, language: str | None) -> str:
    if not slot.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="O servidor já está transcrevendo outro áudio. Tente novamente.")
    try:
        return transcribe(path, model_name=model, language=language)
    finally:
        slot.release()


@app.post("/api/transcribe")
async def transcribe_upload(
    file: UploadFile = File(...),
    model: str = Form("small"),
    language: str = Form("auto"),
):
    if model not in MODELS:
        raise HTTPException(400, "Modelo inválido.")
    if language not in ("auto", "pt", "en", "es"):
        raise HTTPException(400, "Idioma inválido.")
    suffix = Path(file.filename or "").suffix.lower()
    # Áudio gravado no navegador geralmente chega como WebM.
    supported = SUPPORTED_EXTENSIONS | {".webm"}
    if suffix not in supported:
        raise HTTPException(415, "Formato não suportado.")
    path = None
    size = 0
    try:
        with tempfile.NamedTemporaryFile(prefix="audio_scriber_mobile_", suffix=suffix, delete=False) as dest:
            path = Path(dest.name)
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > MAX_BYTES:
                    raise HTTPException(413, "Áudio muito grande. Limite: 75 MB.")
                dest.write(chunk)
        if not size:
            raise HTTPException(400, "O arquivo está vazio.")
        try:
            content = await run_in_threadpool(_process, str(path), model, None if language == "auto" else language)
        except HTTPException:
            raise
        except Exception as exc:
            # Não expor caminhos locais ou detalhes internos no retorno.
            raise HTTPException(422, "Não foi possível transcrever este áudio. Verifique o formato e tente novamente.") from exc
        return JSONResponse({"text": content})
    finally:
        await file.close()
        if path is not None:
            path.unlink(missing_ok=True)
