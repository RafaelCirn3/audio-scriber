"""Audio Scriber: interface Windows em Tkinter."""
import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from tkinter.scrolledtext import ScrolledText

from audio_scriber.recording import AudioRecorder
from audio_scriber.transcription import LANGUAGES, MODELS, transcribe, validate_audio


class AudioScriber(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Audio Scriber")
        self.geometry("820x640")
        self.minsize(700, 560)
        self.configure(bg="#f4f6fa")

        self.recorder = AudioRecorder()
        self.selected_audio: Path | None = None
        self.temporary_audio: Path | None = None
        self.transcript = ""
        self.busy = False
        self.events = queue.Queue()
        self.model_var = tk.StringVar(value="small")
        self.language_var = tk.StringVar(value="Automático")
        self.audio_var = tk.StringVar(value="Nenhum áudio selecionado")
        self.status_var = tk.StringVar(value="Pronto para começar.")
        self._setup_style()
        self._build_ui()
        self.after(100, self._poll_events)
        self.protocol("WM_DELETE_WINDOW", self._close)

    def _setup_style(self):
        style = ttk.Style(self)
        if "clam" in style.theme_names():
            style.theme_use("clam")
        style.configure("Primary.TButton", font=("Segoe UI", 12, "bold"), padding=(18, 16))
        style.configure("Secondary.TButton", font=("Segoe UI", 12, "bold"), padding=(18, 16))
        style.configure("Action.TButton", font=("Segoe UI", 10, "bold"), padding=(15, 11))
        style.configure("TCombobox", padding=6)
        style.configure("TProgressbar", thickness=8)

    def _build_ui(self):
        outer = tk.Frame(self, bg="#f4f6fa", padx=30, pady=25)
        outer.pack(fill="both", expand=True)
        tk.Label(outer, text="Audio Scriber", font=("Segoe UI", 25, "bold"),
                 bg="#f4f6fa", fg="#18243b").pack(anchor="w")
        tk.Label(outer, text="Transcreva gravações e arquivos no seu próprio computador.",
                 font=("Segoe UI", 11), bg="#f4f6fa", fg="#58677f").pack(anchor="w", pady=(2, 20))

        options = tk.Frame(outer, bg="#f4f6fa")
        options.pack(fill="x", pady=(0, 12))
        tk.Label(options, text="Modelo:", bg="#f4f6fa", font=("Segoe UI", 10)).pack(side="left")
        ttk.Combobox(options, textvariable=self.model_var, values=MODELS, state="readonly",
                     width=12).pack(side="left", padx=(8, 26))
        tk.Label(options, text="Idioma:", bg="#f4f6fa", font=("Segoe UI", 10)).pack(side="left")
        ttk.Combobox(options, textvariable=self.language_var, values=list(LANGUAGES),
                     state="readonly", width=16).pack(side="left", padx=8)

        buttons = tk.Frame(outer, bg="#f4f6fa")
        buttons.pack(fill="x", pady=(4, 12))
        buttons.columnconfigure(0, weight=1)
        buttons.columnconfigure(1, weight=1)
        self.record_button = ttk.Button(buttons, text="🎙  Grave um áudio",
                                        style="Primary.TButton", command=self._record)
        self.record_button.grid(row=0, column=0, sticky="ew", padx=(0, 8))
        self.upload_button = ttk.Button(buttons, text="↑  Envie um áudio",
                                        style="Secondary.TButton", command=self._upload)
        self.upload_button.grid(row=0, column=1, sticky="ew", padx=(8, 0))

        tk.Label(outer, textvariable=self.audio_var, anchor="w", bg="#f4f6fa",
                 fg="#43516b", font=("Segoe UI", 10), wraplength=740).pack(fill="x", pady=(0, 10))
        actions = tk.Frame(outer, bg="#f4f6fa")
        actions.pack(fill="x", pady=(0, 10))
        self.transcribe_button = ttk.Button(actions, text="Transcrever áudio",
                                            style="Action.TButton", command=self._transcribe)
        self.transcribe_button.pack(side="left")
        self.save_button = ttk.Button(actions, text="Salvar .txt", style="Action.TButton",
                                     command=self._save, state="disabled")
        self.save_button.pack(side="left", padx=10)

        self.progress = ttk.Progressbar(outer, mode="indeterminate")
        self.progress.pack(fill="x", pady=(0, 8))
        tk.Label(outer, textvariable=self.status_var, anchor="w", bg="#f4f6fa",
                 fg="#58677f", font=("Segoe UI", 10)).pack(fill="x", pady=(0, 9))
        tk.Label(outer, text="Transcrição", bg="#f4f6fa", fg="#18243b",
                 font=("Segoe UI", 12, "bold")).pack(anchor="w", pady=(0, 5))
        self.preview = ScrolledText(outer, wrap="word", font=("Segoe UI", 11),
                                    relief="solid", borderwidth=1, padx=12, pady=12,
                                    bg="#ffffff", fg="#243149", height=12)
        self.preview.pack(fill="both", expand=True)
        tk.Label(outer, text="O primeiro uso requer internet para baixar o modelo. O processamento é local.",
                 bg="#f4f6fa", fg="#64748b", font=("Segoe UI", 9)).pack(anchor="w", pady=(9, 0))

    def _reset_text(self):
        self.transcript = ""
        self.preview.delete("1.0", "end")
        self.save_button.configure(state="disabled")

    def _record(self):
        if self.busy:
            return
        if self.recorder.is_recording:
            try:
                self.selected_audio = self.recorder.stop()
                self.temporary_audio = self.selected_audio
                self.audio_var.set("Gravação pronta: " + self.selected_audio.name)
                self.status_var.set("Gravação encerrada. Clique em Transcrever áudio.")
            except Exception as exc:
                messagebox.showerror("Gravação", str(exc))
            finally:
                self.record_button.configure(text="🎙  Grave um áudio")
                self.upload_button.configure(state="normal")
            return
        try:
            self._remove_temp()
            self._reset_text()
            self.recorder.start()
            self.record_button.configure(text="■  Parar gravação")
            self.upload_button.configure(state="disabled")
            self.audio_var.set("Gravando pelo microfone...")
            self.status_var.set("Gravação em andamento. Clique em Parar gravação.")
        except Exception as exc:
            messagebox.showerror("Microfone indisponível", str(exc))
            self.status_var.set("Falha ao acessar o microfone.")

    def _upload(self):
        if self.busy or self.recorder.is_recording:
            return
        path = filedialog.askopenfilename(
            title="Escolha um áudio ou vídeo",
            filetypes=[("Arquivos de áudio/vídeo", "*.wav *.mp3 *.m4a *.aac *.ogg *.opus *.flac *.mp4 *.mov"),
                       ("Todos os arquivos", "*.*")]
        )
        if not path:
            return
        try:
            audio = validate_audio(path)
        except (ValueError, FileNotFoundError, OSError) as exc:
            messagebox.showerror("Arquivo inválido", str(exc))
            return
        self._remove_temp()
        self.selected_audio = audio
        self._reset_text()
        self.audio_var.set("Selecionado: " + str(audio))
        self.status_var.set("Arquivo pronto para transcrição.")

    def _transcribe(self):
        if self.busy or self.recorder.is_recording:
            return
        if self.selected_audio is None:
            messagebox.showinfo("Selecione um áudio", "Grave um áudio ou escolha um arquivo antes de transcrever.")
            return
        try:
            validate_audio(self.selected_audio)
        except (ValueError, FileNotFoundError, OSError) as exc:
            messagebox.showerror("Áudio inválido", str(exc))
            return
        self._reset_text()
        self.busy = True
        self.transcribe_button.configure(state="disabled")
        self.upload_button.configure(state="disabled")
        self.record_button.configure(state="disabled")
        self.progress.start(12)
        self.status_var.set("Transcrevendo... A primeira execução também baixa o modelo.")
        path = self.selected_audio
        model = self.model_var.get()
        lang = LANGUAGES[self.language_var.get()]

        def work():
            try:
                text = transcribe(path, model_name=model, language=lang)
                self.events.put(("success", text))
            except Exception as exc:
                self.events.put(("error", str(exc)))

        threading.Thread(target=work, daemon=True).start()

    def _poll_events(self):
        try:
            self.recorder.poll()
            while True:
                kind, value = self.events.get_nowait()
                self.busy = False
                self.progress.stop()
                self.record_button.configure(state="normal")
                self.upload_button.configure(state="normal")
                self.transcribe_button.configure(state="normal")
                if kind == "success":
                    self.transcript = value
                    self.preview.insert("1.0", value)
                    self.save_button.configure(state="normal")
                    self.status_var.set("Transcrição concluída. Escolha onde salvar o arquivo .txt.")
                    self._save()
                else:
                    self.status_var.set("Falha na transcrição.")
                    messagebox.showerror("Erro na transcrição", value)
        except queue.Empty:
            pass
        finally:
            if self.winfo_exists():
                self.after(100, self._poll_events)

    def _save(self):
        if not self.transcript:
            return
        suggested_name = (self.selected_audio.stem if self.selected_audio else "transcricao") + ".txt"
        destination = filedialog.asksaveasfilename(
            title="Salvar transcrição", defaultextension=".txt",
            initialfile=suggested_name, filetypes=[("Arquivo de texto", "*.txt")]
        )
        if not destination:
            self.status_var.set("Transcrição disponível. Clique em Salvar .txt quando desejar.")
            return
        try:
            Path(destination).write_text(self.transcript, encoding="utf-8")
            self.status_var.set("Arquivo salvo em: " + destination)
            messagebox.showinfo("Transcrição salva", "Arquivo .txt salvo com sucesso.")
        except OSError as exc:
            messagebox.showerror("Erro ao salvar", str(exc))

    def _remove_temp(self):
        if self.temporary_audio is not None:
            self.temporary_audio.unlink(missing_ok=True)
            self.temporary_audio = None

    def _close(self):
        if self.busy:
            messagebox.showinfo("Transcrição em andamento", "Aguarde a transcrição terminar antes de fechar.")
            return
        if self.recorder.is_recording:
            try:
                self.recorder.stop()
            except Exception:
                pass
        self._remove_temp()
        self.destroy()


if __name__ == "__main__":
    AudioScriber().mainloop()
