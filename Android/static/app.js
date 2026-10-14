const $ = id => document.getElementById(id);
let selectedFile = null, recorder = null, stream = null, chunks = [], busy = false, currentText = "";
const recordButton = $("record"), uploadButton = $("upload"), input = $("file");
const status = value => { $("status").textContent = value; };
function reset() { currentText = ""; $("result").value = ""; $("download").disabled = true; }
function setFile(file) {
  if (!file) return;
  selectedFile = file;
  reset();
  $("selected").textContent = file.name + " (" + (file.size / 1048576).toFixed(1) + " MB)";
  $("transcribe").disabled = file.size === 0 || file.size > 75 * 1048576;
  status(file.size > 75 * 1048576 ? "O arquivo excede 75 MB." : "Áudio pronto para transcrever.");
}
input.addEventListener("change", () => setFile(input.files?.[0]));
uploadButton.addEventListener("click", () => input.click());
recordButton.addEventListener("click", async () => {
  if (busy) return;
  if (recorder && recorder.state === "recording") {
    recordButton.disabled = true;
    recorder.stop();
    return;
  }
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    status("Gravação no navegador requer HTTPS e suporte ao microfone. Use Envie um áudio.");
    return;
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mime = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4"].find(type => MediaRecorder.isTypeSupported(type));
    recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
    chunks = [];
    recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data); };
    recorder.onstop = () => {
      const type = recorder.mimeType || "audio/webm";
      const extension = type.includes("mp4") ? ".m4a" : ".webm";
      setFile(new File(chunks, "gravacao_" + Date.now() + extension, {type}));
      stream?.getTracks().forEach(t => t.stop());
      stream = null; recorder = null;
      recordButton.disabled = false; recordButton.textContent = "● Grave um áudio";
      uploadButton.disabled = false;
    };
    recorder.onerror = () => { status("Falha ao gravar áudio."); stream?.getTracks().forEach(t => t.stop()); };
    recorder.start();
    reset(); selectedFile = null; $("transcribe").disabled = true;
    uploadButton.disabled = true;
    recordButton.textContent = "■ Parar gravação";
    status("Gravando… toque em Parar gravação ao concluir.");
  } catch (err) {
    stream?.getTracks().forEach(t => t.stop());
    status("Não foi possível usar o microfone. Confira a permissão ou envie um arquivo.");
  }
});
$("transcribe").addEventListener("click", async () => {
  if (!selectedFile || busy) return;
  busy = true; reset();
  $("transcribe").disabled = true; uploadButton.disabled = true; recordButton.disabled = true;
  status("Transcrevendo… o processamento pode levar alguns minutos.");
  const body = new FormData();
  body.append("file", selectedFile);
  body.append("model", $("model").value);
  body.append("language", $("language").value);
  try {
    const response = await fetch("/api/transcribe", {method:"POST",body});
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || "Falha na transcrição.");
    currentText = data.text;
    $("result").value = currentText;
    $("download").disabled = false;
    status("Transcrição concluída. Toque em Baixar .txt.");
  } catch (err) {
    status("Erro: " + err.message);
  } finally {
    busy = false; uploadButton.disabled = false; recordButton.disabled = false;
    $("transcribe").disabled = !selectedFile;
  }
});
$("download").addEventListener("click", () => {
  if (!currentText) return;
  const blob = new Blob([currentText], {type:"text/plain;charset=utf-8"});
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = (selectedFile?.name || "audio").replace(/\.[^.]+$/, "") + "_transcricao.txt";
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
