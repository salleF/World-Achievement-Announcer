"""Gera os áudios da voz do SinosDoMundo.

O plugin chama este script como um programa separado, uma vez por narrador:
  python gerar_voz.py --motor piper  --modelo <voz.onnx> --saida <pasta>
  python gerar_voz.py --motor kokoro --modelo <kokoro.onnx> --vozes <voices.bin> --voz "pf_dora:0.6,if_sara:0.4" --saida <pasta>
e manda pela entrada padrão uma lista JSON de pedidos:
  [{"id": "a1b2c3", "texto": "Seus contribuidores foram", "tipo": "texto"},
   {"id": "d4e5f6", "texto": "AugustinhoGames", "tipo": "nome"}]
Para cada pedido grava <saida>/<id>.ogg e devolve na saída padrão:
  [{"id": "a1b2c3", "duracao_ms": 1630}, ...]

Requisitos: pip install piper-tts soundfile (Piper) e kokoro-onnx (Kokoro)
"""

import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
import soundfile as sf


def texto_falado(nome: str) -> str:
    """AugustinhoGames -> Augustinho Games, xX_Pedro_Xx -> xX Pedro Xx, Lulu2009 -> Lulu 2009."""
    nome = nome.replace("_", " ")
    nome = re.sub(r"(?<=[a-zà-ÿ])(?=[A-Z])", " ", nome)
    nome = re.sub(r"(?<=[A-Za-zÀ-ÿ])(?=\d)|(?<=\d)(?=[A-Za-zÀ-ÿ])", " ", nome)
    return " ".join(nome.split())


def aparar(audio: np.ndarray, sr: int) -> np.ndarray:
    """Tira o silêncio do começo e do fim, deixando 10 ms de margem."""
    if audio.size == 0:
        return audio
    limite = 0.02 * np.max(np.abs(audio))
    sinal = np.where(np.abs(audio) > limite)[0]
    if sinal.size == 0:
        return audio[:0]
    margem = int(0.01 * sr)
    return audio[max(0, sinal[0] - margem): sinal[-1] + margem]


def motor_piper(opcoes):
    from piper import PiperVoice

    voz = PiperVoice.load(opcoes.modelo)
    sr = voz.config.sample_rate

    def falar(texto):
        pedacos = [np.frombuffer(c.audio_int16_bytes, dtype=np.int16) for c in voz.synthesize(texto)]
        return (np.concatenate(pedacos).astype(np.float32) / 32768 if pedacos else np.zeros(0, np.float32)), sr

    return falar


def motor_kokoro(opcoes):
    from kokoro_onnx import Kokoro

    kokoro = Kokoro(opcoes.modelo, opcoes.vozes)
    # "pf_dora" ou uma mistura "pf_dora:0.6,if_sara:0.4"
    partes = [p.split(":") for p in opcoes.voz.split(",") if p.strip()]
    if len(partes) == 1 and len(partes[0]) == 1:
        estilo = partes[0][0].strip()
    else:
        pesos = [(nome.strip(), float(peso) if peso else 1.0) for nome, *resto in partes for peso in [resto[0] if resto else ""]]
        total = sum(p for _, p in pesos)
        estilo = sum(kokoro.get_voice_style(nome) * (p / total) for nome, p in pesos)

    def falar(texto):
        audio, sr = kokoro.create(texto, voice=estilo, speed=opcoes.velocidade, lang=opcoes.idioma)
        return np.asarray(audio, dtype=np.float32), sr

    return falar


def main() -> None:
    args = argparse.ArgumentParser()
    args.add_argument("--motor", default="piper", choices=["piper", "kokoro"])
    args.add_argument("--modelo", required=True)
    args.add_argument("--vozes", default="")
    args.add_argument("--voz", default="")
    args.add_argument("--velocidade", type=float, default=1.0)
    args.add_argument("--idioma", default="pt-br")
    args.add_argument("--saida", required=True)
    opcoes = args.parse_args()

    pedidos = json.loads(sys.stdin.buffer.read().decode("utf-8"))
    falar = motor_kokoro(opcoes) if opcoes.motor == "kokoro" else motor_piper(opcoes)
    saida = Path(opcoes.saida)
    saida.mkdir(parents=True, exist_ok=True)

    resultados = []
    for pedido in pedidos:
        texto = pedido["texto"]
        if pedido.get("tipo") == "nome":
            texto = texto_falado(texto)
        audio, sr = falar(texto)
        audio = aparar(audio, sr)
        if audio.size == 0:
            print(f"Sem áudio para: {texto}", file=sys.stderr)
            continue
        audio = audio / np.max(np.abs(audio)) * 0.9
        sf.write(str(saida / f"{pedido['id']}.ogg"), audio, sr, format="OGG", subtype="VORBIS")
        resultados.append({"id": pedido["id"], "duracao_ms": round(len(audio) / sr * 1000)})

    sys.stdout.write(json.dumps(resultados))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
