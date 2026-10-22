"""Estende a música de vitória com uma continuação baixa, feita com o próprio som dela, para tocar por
baixo da voz.

Uso: ../voz/.venv/Scripts/python estender_musica.py <musica original> [duracao total em segundos]
Grava src/main/resources/pacote/assets/sinos/sounds/musica/conquista.ogg (mono) e saida/conquista_estendida.wav.

Como funciona:
  - acha no final da música um trecho em que o acorde fica soando sem ataques (o "colchão");
  - estica esse trecho por síntese granular (pedacinhos sobrepostos com transição suave), filtra os agudos
    e abaixa o volume, para virar um fundo contínuo no mesmo acorde e timbre;
  - repete de leve um ataque de sino da própria música, uma oitava abaixo em vezes alternadas;
  - o fundo entra crescendo enquanto a fanfarra original ainda está diminuindo, e tudo some no final.
"""

import sys
from pathlib import Path

import numpy as np
import soundfile as sf

RAIZ = Path(__file__).resolve().parents[2]
SAIDA_PACOTE = RAIZ / "src" / "main" / "resources" / "pacote" / "assets" / "sinos" / "sounds" / "musica" / "conquista.ogg"
RNG = np.random.default_rng(11)

# Escolhas para a música de vitória atual (medidas com a análise de ataques e notas)
COLCHAO = (3.45, 4.15)       # acorde final estável (fá maior com sétima), sem ataques
SINO = (3.25, 3.95)          # ataque de sino no mesmo acorde
ENTRADA_FUNDO = (4.3, 5.8)   # o fundo cresce enquanto a fanfarra some
SINOS_A_CADA = 2.2
VOLUME_FUNDO_RMS = 0.045     # a fanfarra fica perto de 0,12 RMS: o fundo fica uns 8 dB abaixo
VOLUME_SINO = 0.22
SUMIR = 2.0


def granular(trecho: np.ndarray, sr: int, duracao: float) -> np.ndarray:
    """Estica o trecho: grãos de 0,35 s com janela de Hann, sobreposição de 4 vezes e início sorteado."""
    grao = int(0.35 * sr)
    passo = grao // 4
    janela = np.hanning(grao)
    saida = np.zeros(int(duracao * sr) + grao)
    for i in range(0, len(saida) - grao, passo):
        inicio = RNG.integers(0, len(trecho) - grao)
        saida[i:i + grao] += trecho[inicio:inicio + grao] * janela * RNG.uniform(0.85, 1.0)
    return saida[: int(duracao * sr)]


def passa_baixa(x: np.ndarray, sr: int, corte: float) -> np.ndarray:
    espectro = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / sr)
    return np.fft.irfft(espectro / (1 + (f / corte) ** 4), len(x))


def oitava_abaixo(x: np.ndarray) -> np.ndarray:
    """Reamostra para tocar uma oitava abaixo (fica com o dobro da duração)."""
    return np.interp(np.arange(0, len(x), 0.5), np.arange(len(x)), x)


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    total = float(sys.argv[2]) if len(sys.argv) > 2 else 17.5
    audio, sr = sf.read(sys.argv[1])
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    audio = audio / np.max(np.abs(audio)) * 0.9

    mix = np.zeros(int(total * sr))
    mix[: len(audio)] += audio

    # Fundo contínuo a partir do colchão
    colchao = audio[int(COLCHAO[0] * sr): int(COLCHAO[1] * sr)]
    inicio_fundo = ENTRADA_FUNDO[0]
    fundo = granular(colchao, sr, total - inicio_fundo)
    fundo = passa_baixa(fundo, sr, 1800)
    fundo *= VOLUME_FUNDO_RMS / np.sqrt(np.mean(fundo ** 2))
    t = np.arange(len(fundo)) / sr
    fundo *= np.clip(t / (ENTRADA_FUNDO[1] - ENTRADA_FUNDO[0]), 0, 1) ** 2
    mix[int(inicio_fundo * sr):] += fundo

    # Sinos da própria música, de leve
    sino = audio[int(SINO[0] * sr): int(SINO[1] * sr)].copy()
    sino *= np.linspace(1, 0, len(sino)) ** 1.5
    sino = passa_baixa(sino, sr, 4000)
    sino = sino / np.max(np.abs(sino))
    vez = 0
    for momento in np.arange(ENTRADA_FUNDO[1] + 0.4, total - SUMIR - 0.5, SINOS_A_CADA):
        som = oitava_abaixo(sino) if vez % 2 else sino
        i = int(momento * sr)
        fim = min(len(mix), i + len(som))
        mix[i:fim] += som[: fim - i] * VOLUME_SINO * (0.8 if vez % 2 else 1.0)
        vez += 1

    # Some no final
    n = int(SUMIR * sr)
    mix[-n:] *= np.linspace(1, 0, n) ** 2
    mix = mix / np.max(np.abs(mix)) * 0.9

    SAIDA_PACOTE.parent.mkdir(parents=True, exist_ok=True)
    # Em blocos: gravar um OGG longo de uma vez estoura a pilha da libsndfile no Windows
    with sf.SoundFile(str(SAIDA_PACOTE), "w", sr, 1, format="OGG", subtype="VORBIS") as ogg:
        for i in range(0, len(mix), sr):
            ogg.write(mix[i:i + sr].astype(np.float32))
    copia = Path(__file__).parent / "saida"
    copia.mkdir(exist_ok=True)
    sf.write(str(copia / "conquista_estendida.wav"), mix.astype(np.float32), sr)
    print(f"{len(mix) / sr:.1f} s gravados em {SAIDA_PACOTE}")


if __name__ == "__main__":
    main()
