#!/usr/bin/env python3
"""Normalize the official legacy Portuguese Vosk model into the current Vosk directory layout."""
import sys
import zipfile
from pathlib import Path

def prepare(source: Path, destination: Path) -> None:
    with zipfile.ZipFile(source) as archive:
        files = {name: name for name in archive.namelist() if not name.endswith("/")}
        # The Portuguese 0.3 archive is a legacy Kaldi layout with files at model root.
        roots = sorted({name[:-len("final.mdl")] for name in files if name.endswith("final.mdl")})
        if len(roots) != 1:
            raise ValueError(f"Expected exactly one model root, found: {roots}")
        root = roots[0]
        def locate(relative: str) -> str | None:
            for candidate in (root + relative, root + "am/" + relative,
                              root + "conf/" + relative, root + "graph/" + relative,
                              root + "graph/phones/" + relative):
                if candidate in files:
                    return candidate
            return None
        mapping = {}
        for required, output in (
            ("final.mdl", "am/final.mdl"),
            ("mfcc.conf", "conf/mfcc.conf"),
            ("HCLr.fst", "graph/HCLr.fst"),
            ("Gr.fst", "graph/Gr.fst"),
            ("word_boundary.int", "graph/phones/word_boundary.int"),
        ):
            entry = locate(required)
            if entry is None:
                raise ValueError(f"Missing essential source file: {required}")
            mapping[entry] = output
        for optional, output in (
            ("phones.txt", "graph/phones.txt"),
            ("disambig_tid.int", "graph/disambig_tid.int"),
        ):
            entry = locate(optional)
            if entry:
                mapping[entry] = output
        for name in files:
            if name.startswith(root + "ivector/"):
                mapping[name] = name[len(root):]
        config = locate("model.conf")
        if config:
            mapping[config] = "conf/model.conf"
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as output:
            for original, normalized in sorted(mapping.items()):
                output.writestr("vosk-model-small-pt-0.3/" + normalized, archive.read(original))
            if not config:
                output.writestr("vosk-model-small-pt-0.3/conf/model.conf",
                                "--beam=10.0\n--max-active=3000\n--lattice-beam=2.0\n")
    with zipfile.ZipFile(destination) as verify:
        names = set(verify.namelist())
        base = "vosk-model-small-pt-0.3/"
        assert all(base + x in names for x in (
            "am/final.mdl", "conf/mfcc.conf", "conf/model.conf",
            "graph/HCLr.fst", "graph/Gr.fst", "graph/phones/word_boundary.int"))
    print(f"Normalized model: {destination} ({destination.stat().st_size} bytes)")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: prepare_vosk_model.py source.zip output.zip")
    prepare(Path(sys.argv[1]), Path(sys.argv[2]))
