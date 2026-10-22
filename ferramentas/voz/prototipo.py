"""Protótipo da voz do sino: compara a frase falada inteira com a frase montada por palavras e sílabas.

Uso: .venv/Scripts/python prototipo.py [faber|cadu]

Gera em saida/:
  1_referencia_<voz>.wav   frase inteira, como o Simple Voice Chat falaria
  2_palavras_silabas_<voz>.wav   palavras comuns inteiras + sílabas para o resto (o plano)
  3_so_silabas_<voz>.wav   tudo por sílaba, para comparar

As montagens simulam o Minecraft: cada pedaço só pode começar num tick (50 ms) e pedaços podem se sobrepor.
"""

import re
import sys
import wave
from pathlib import Path

import numpy as np
from piper import PiperVoice, SynthesisConfig

FRASE = ("Alerta, alerta! O boss Ender Dragon foi morto. "
         "Seus contribuidores foram AugustinhoGames, GeronimoSantos e Nonamename.")

# Palavras que entrariam inteiras no banco
BANCO_PALAVRAS = {
    "alerta", "o", "a", "boss", "ender", "dragon", "foi", "morto", "morta",
    "seus", "suas", "contribuidores", "foram", "e", "conquista", "mundial",
}

TICK_S = 0.05
PAUSA_PALAVRA = 1   # ticks entre palavras
PAUSA_VIRGULA = 5
PAUSA_PONTO = 9
VELOCIDADE_SILABA = 0.85  # sílabas isoladas saem lentas; acelera um pouco

VOGAIS = set("aeiouáéíóúâêôãõàüy")
ENCONTROS = {"bl", "br", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "pl", "pr", "tr", "vr"}
DIGRAFOS = {"ch", "lh", "nh", "qu", "gu"}

PASTA = Path(__file__).parent


def silabas(palavra: str) -> list[str]:
    """Separação silábica simplificada do português. Boa o bastante para nomes de jogadores."""
    p = palavra.lower()
    # Núcleos: grupos de vogais seguidas
    nucleos = [(m.start(), m.end()) for m in re.finditer(r"[aeiouáéíóúâêôãõàüy]+", p)]
    if not nucleos:
        return [p]
    cortes = []
    for (_, fim), (inicio, _) in zip(nucleos, nucleos[1:]):
        meio = p[fim:inicio]
        if len(meio) <= 1:
            corte = fim
        elif meio[-2:] in ENCONTROS or meio[-2:] in DIGRAFOS:
            corte = inicio - 2
        else:
            corte = inicio - 1
        cortes.append(corte)
    partes = []
    anterior = 0
    for c in cortes:
        partes.append(p[anterior:c])
        anterior = c
    partes.append(p[anterior:])
    return [s for s in partes if s]


def separar_nome(token: str) -> list[str]:
    """AugustinhoGames -> Augustinho, Games."""
    return re.findall(r"[A-ZÁÉÍÓÚÂÊÔÃÕ]?[a-záéíóúâêôãõàüç]+|[A-Z]+(?![a-z])|\d+", token) or [token]


class Sintetizador:
    def __init__(self, voz: str):
        self.voice = PiperVoice.load(str(PASTA / "vozes" / f"pt_BR-{voz}-medium.onnx"))
        self.sr = self.voice.config.sample_rate
        self.cache: dict[tuple[str, float], np.ndarray] = {}

    def falar(self, texto: str, velocidade: float = 1.0) -> np.ndarray:
        chave = (texto, velocidade)
        if chave not in self.cache:
            cfg = SynthesisConfig(length_scale=velocidade)
            pedacos = [np.frombuffer(c.audio_int16_bytes, dtype=np.int16) for c in self.voice.synthesize(texto, cfg)]
            audio = np.concatenate(pedacos).astype(np.float32) / 32768
            self.cache[chave] = aparar(audio, self.sr)
        return self.cache[chave]


def aparar(audio: np.ndarray, sr: int) -> np.ndarray:
    """Tira o silêncio do começo e do fim, deixando 10 ms de margem."""
    limite = 0.02 * np.max(np.abs(audio)) if audio.size else 0
    sinal = np.where(np.abs(audio) > limite)[0]
    if sinal.size == 0:
        return audio[:0]
    margem = int(0.01 * sr)
    return audio[max(0, sinal[0] - margem): sinal[-1] + margem]


def montar(sint: Sintetizador, usar_banco: bool) -> tuple[np.ndarray, list[str]]:
    """Coloca cada pedaço no tick mais próximo do tempo real, como o plugin faria."""
    sr = sint.sr
    tick = int(TICK_S * sr)
    eventos: list[tuple[int, np.ndarray]] = []
    roteiro: list[str] = []
    tempo = 0.0  # em ticks, contínuo

    for token in re.findall(r"[\wÀ-ÿ]+|[,.!?]", FRASE):
        if token in ",":
            tempo += PAUSA_VIRGULA
            roteiro.append("<pausa>")
            continue
        if token in ".!?":
            tempo += PAUSA_PONTO
            roteiro.append("<pausa longa>")
            continue
        for parte in separar_nome(token):
            if usar_banco and parte.lower() in BANCO_PALAVRAS:
                unidades = [(parte.lower(), 1.0)]
            else:
                unidades = [(s, VELOCIDADE_SILABA) for s in silabas(parte)]
            for texto, vel in unidades:
                audio = sint.falar(texto, vel)
                inicio = round(tempo)
                eventos.append((inicio, audio))
                roteiro.append(f"{texto}@{inicio}")
                tempo += len(audio) / tick
            tempo += PAUSA_PALAVRA

    total = max(i * tick + len(a) for i, a in eventos)
    mix = np.zeros(total + sr // 2, dtype=np.float32)
    for inicio, audio in eventos:
        mix[inicio * tick: inicio * tick + len(audio)] += audio
    return mix, roteiro


def salvar(caminho: Path, audio: np.ndarray, sr: int) -> None:
    pico = np.max(np.abs(audio)) or 1
    dados = (audio / pico * 0.9 * 32767).astype(np.int16)
    with wave.open(str(caminho), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(dados.tobytes())


def main() -> None:
    voz = sys.argv[1] if len(sys.argv) > 1 else "faber"
    sint = Sintetizador(voz)
    saida = PASTA / "saida"
    saida.mkdir(exist_ok=True)

    salvar(saida / f"1_referencia_{voz}.wav", sint.falar(FRASE), sint.sr)

    for nome, usar_banco in (("2_palavras_silabas", True), ("3_so_silabas", False)):
        audio, roteiro = montar(sint, usar_banco)
        salvar(saida / f"{nome}_{voz}.wav", audio, sint.sr)
        print(f"{nome}: {len(audio) / sint.sr:.1f} s")
        print("  " + " ".join(roteiro))

    print("Unidades distintas sintetizadas:", len(sint.cache))


if __name__ == "__main__":
    main()
