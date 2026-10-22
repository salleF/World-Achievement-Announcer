"""Compõe a música de conquista do SinosDoMundo (sintetizada por código, sem direitos de terceiros).

Uso: ../voz/.venv/Scripts/python gerar_musica.py
Grava saida/conquista.ogg (mono) e saida/conquista.wav. Para usar no plugin, copie o .ogg para
src/main/resources/pacote/assets/sinos/sounds/musica/conquista.ogg (hoje lá está o áudio escolhido pelo usuário).

A música começa na primeira batida do sino (as batidas vêm a cada 0,4 s):
  0,0-1,6 s   três ataques de metais junto com as batidas, rufar de tímpano e prato crescendo
  1,6-3,2 s   clímax: acorde de orquestra em fá sustenido maior, coro, prato e cascata de sinos
  3,2-13,0 s  fundo para a voz: cordas e coro baixos, tímpano a cada compasso
  13,0-16,5 s subida final com prato, último ataque e cauda
"""

from pathlib import Path

import numpy as np
import soundfile as sf

SR = 32000
RAIZ = Path(__file__).resolve().parents[2]
SAIDA_PACOTE = Path(__file__).parent / "saida" / "conquista.ogg"
RNG = np.random.default_rng(42)


def nota(nome: str) -> float:
    """'F#4' -> frequência em Hz."""
    tons = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    return 440.0 * 2 ** ((tons[nome[:-1]] + 12 * (int(nome[-1]) - 4)) / 12)


def tempo(dur: float) -> np.ndarray:
    return np.arange(int(dur * SR)) / SR


def adsr(n: int, a: float, r: float) -> np.ndarray:
    t = np.arange(n) / SR
    return np.minimum(1, t / max(a, 1e-4)) * np.clip((n / SR - t) / max(r, 1e-4), 0, 1)


def serra(f: float, t: np.ndarray, harmonicos: int, brilho) -> np.ndarray:
    onda = np.zeros_like(t)
    for k in range(1, harmonicos + 1):
        if f * k > SR / 2 - 800:
            break
        onda += (1 / k) * np.power(brilho, k - 1) * np.sin(2 * np.pi * f * k * t)
    return onda


def metais(fs: list[str], dur: float, forca: float = 1.0) -> np.ndarray:
    """Naipe de metais: 3 vozes desafinadas por nota, brilho que abre forte no ataque."""
    t = tempo(dur)
    brilho = 0.45 + 0.4 * forca * np.exp(-t * 2.2) * np.minimum(1, t / 0.03)
    vib = 1 + 0.005 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.5)
    som = np.zeros_like(t)
    for f in fs:
        for d in (-0.006, 0.0, 0.007):
            som += serra(nota(f) * (1 + d), t * vib, 16, brilho)
    return som / (3 * len(fs)) * adsr(len(t), 0.025, min(0.4, dur * 0.4))


def cordas(fs: list[str], dur: float) -> np.ndarray:
    t = tempo(dur)
    som = np.zeros_like(t)
    for f in fs:
        for d in (-0.008, -0.003, 0.003, 0.008):
            som += serra(nota(f) * (1 + d), t, 8, 0.55)
    return som / (4 * len(fs)) * adsr(len(t), 0.5, 0.8)


def coro(fs: list[str], dur: float) -> np.ndarray:
    """Coro em "aah": serra filtrada por três formantes da vogal A."""
    t = tempo(dur)
    som = np.zeros_like(t)
    for f in fs:
        f0 = nota(f)
        for d in (-0.005, 0.005):
            fv = f0 * (1 + d)
            vib = 1 + 0.006 * np.sin(2 * np.pi * (5 + d * 100) * t)
            for k in range(1, 40):
                fk = fv * k
                if fk > 5000:
                    break
                ganho = sum(np.exp(-((fk - fo) / bw) ** 2) * g for fo, bw, g in ((800, 120, 1.0), (1150, 140, 0.6), (2900, 250, 0.25)))
                som += ganho / k ** 0.3 * np.sin(2 * np.pi * fk * t * vib)
    return som / (2 * len(fs)) * adsr(len(t), 0.35, 0.9)


def sino(f: float, dur: float = 3.0, forca: float = 1.0) -> np.ndarray:
    """Sino tubular: parciais inarmônicas com decaimentos diferentes."""
    t = tempo(dur)
    parciais = [(1.0, 1.0, 1.1), (0.7, 2.0, 1.8), (0.5, 2.76, 2.3), (0.35, 4.07, 3.2), (0.25, 5.4, 4.5), (0.4, 0.5, 0.8)]
    som = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * d * (1.2 - 0.2 * forca)) for a, r, d in parciais)
    return som * np.minimum(1, t / 0.002)


def celesta(f: float, dur: float = 1.4) -> np.ndarray:
    t = tempo(dur)
    indice = 2.5 * np.exp(-t * 7)
    return np.sin(2 * np.pi * f * t + indice * np.sin(2 * np.pi * 3.5 * f * t)) * np.exp(-t * 3.5) * np.minimum(1, t / 0.002)


def timpano(f: float, dur: float = 1.6, forca: float = 1.0) -> np.ndarray:
    t = tempo(dur)
    fase = 2 * np.pi * np.cumsum(f * (1 + 0.3 * np.exp(-t * 20))) / SR
    corpo = (np.sin(fase) + 0.35 * np.sin(1.5 * fase)) * np.exp(-t * (2.8 / forca))
    batida = RNG.normal(0, 1, len(t)) * np.exp(-t * 35) * 0.4
    return (corpo + batida) * np.minimum(1, t / 0.002) * forca


def prato(dur: float = 3.5, subida: float = 0.0) -> np.ndarray:
    """Prato de ataque; com subida > 0 vira um prato crescendo (swell) que termina no fim do som."""
    t = tempo(dur)
    ruido = RNG.normal(0, 1, len(t))
    ruido = np.diff(ruido, prepend=0)  # tira os graves
    ruido = np.convolve(ruido, np.ones(4) / 4, mode="same")  # e o chiado mais agudo
    metal = sum(np.sin(2 * np.pi * f * t + RNG.uniform(0, 6)) for f in (2630, 3150, 4710, 5930)) * 0.1
    som = ruido * 0.4 + metal
    if subida > 0:
        env = (t / dur) ** 3
    else:
        env = np.exp(-t * 1.3) * np.minimum(1, t / 0.003)
    return som * env


def reverb(sinal: np.ndarray, mistura: float = 0.3) -> np.ndarray:
    """Reverb de sala: 6 pentes em paralelo (vetorizados por blocos) e 2 passa-tudo."""
    def pente(x, d, g):
        y = np.copy(x)
        for i in range(d, len(x), d):
            fim = min(len(x), i + d)
            y[i:fim] += g * y[i - d: fim - d]
        return y

    def passa_tudo(x, d, g):
        y = np.zeros_like(x)
        for i in range(len(x)):
            y[i] = -g * x[i] + (x[i - d] if i >= d else 0) + g * (y[i - d] if i >= d else 0)
        return y

    molhado = sum(pente(sinal, int(SR * d), 0.82) for d in (0.0297, 0.0371, 0.0411, 0.0437, 0.0533, 0.0617)) / 6
    for d in (0.005, 0.0017):
        molhado = passa_tudo(molhado, int(SR * d), 0.7)
    return sinal * (1 - mistura) + molhado * mistura


def main() -> None:
    total = 16.5
    mix = np.zeros(int(total * SR))

    def colocar(som, inicio, volume):
        i = int(inicio * SR)
        fim = min(len(mix), i + len(som))
        mix[i:fim] += som[: fim - i] * volume

    acorde_cheio = ["F#2", "C#3", "F#3", "A#3", "C#4", "F#4"]

    # 0,0-1,6 s: três ataques junto das batidas do sino (0,0 / 0,4 / 0,8) e subida
    for i, (acorde, grave) in enumerate(((["C#4", "F#4", "A#4"], "F#2"), (["D#4", "G#4", "B4"], "B1"), (["C#4", "F4", "G#4"], "C#2"))):
        inicio = i * 0.4
        colocar(metais(acorde + [grave.replace("1", "3").replace("2", "3")], 0.38 if i < 2 else 0.8, 1.0), inicio, 0.95)
        colocar(cordas(acorde, 0.42 if i < 2 else 0.8), inicio, 0.35)
        colocar(timpano(nota(grave.replace("1", "2"))), inicio, 0.95)
        colocar(sino(nota(acorde[1]), 2.0), inicio, 0.3)
    # Rufar de tímpano crescendo (1,0 a 1,6) e prato em subida
    for j in range(14):
        colocar(timpano(nota("C#2"), 0.5, 0.35 + 0.05 * j), 1.0 + j * 0.043, 0.35)
    colocar(prato(1.6, subida=1), 0.0, 0.25)
    # Linha dos metais subindo para o clímax
    for j, f in enumerate(("G#4", "A#4", "C5", "C#5")):
        colocar(metais([f, f.replace("4", "5").replace("5", "5")], 0.16), 0.95 + j * 0.15, 0.7)

    # 1,6-3,2 s: clímax
    climax = 1.6
    colocar(metais(acorde_cheio + ["A#4", "C#5"], 3.2, 1.0), climax, 0.75)
    colocar(metais(["F#3", "C#4", "F#4", "A#4"], 3.0, 0.4), climax + 0.5, 0.45)
    colocar(cordas(["F#3", "A#3", "C#4", "F#4", "A#4"], 3.6), climax, 0.65)
    colocar(coro(["F#3", "C#4", "A#4"], 3.4), climax, 0.75)
    colocar(timpano(nota("F#2"), 2.5, 1.3), climax, 0.95)
    colocar(timpano(nota("C#2"), 1.5), climax + 0.4, 0.5)
    colocar(prato(4.0), climax, 0.25)
    colocar(sino(nota("F#4"), 4.0), climax, 0.35)
    colocar(sino(nota("C#5"), 3.5), climax + 0.12, 0.25)
    for j, f in enumerate(("F#6", "C#6", "A#5", "F#5", "C#5", "A#5", "C#6", "F#6")):
        colocar(celesta(nota(f)), climax + 0.08 * j, 0.14)

    # 3,2-13,0 s: fundo para a voz (fá# - ré#m - si - dó#, 2,2 s por acorde)
    progressao = [
        ["F#3", "A#3", "C#4"], ["D#3", "F#3", "A#3"], ["B2", "D#3", "F#3"], ["C#3", "F3", "G#3"],
    ]
    t_fundo = 3.0
    for k in range(4):
        acorde = progressao[k % 4]
        inicio = t_fundo + k * 2.2
        colocar(cordas(acorde + [acorde[0].replace("3", "2").replace("2", "2")], 2.6), inicio, 0.95)
        colocar(coro(acorde, 2.6), inicio, 0.4)
        colocar(timpano(nota(acorde[0].replace("3", "2")), 1.4, 0.6), inicio, 0.6)
        for j, f in enumerate(acorde + [acorde[1]]):
            colocar(celesta(nota(f.replace("3", "5").replace("2", "5"))), inicio + 0.55 * j, 0.13)
    # Sustenta o dó# (dominante) e prepara o final
    colocar(cordas(["C#3", "F3", "G#3", "C#4"], 2.4), t_fundo + 4 * 2.2, 0.75)
    colocar(coro(["C#3", "G#3", "F3"], 2.4), t_fundo + 4 * 2.2, 0.35)

    # 13,0-16,5 s: subida e último ataque
    final = 13.4
    colocar(prato(2.0, subida=1), final - 2.0, 0.25)
    for j in range(10):
        colocar(timpano(nota("C#2"), 0.5, 0.3 + 0.06 * j), final - 0.9 + j * 0.09, 0.3)
    colocar(metais(acorde_cheio, 3.0, 1.0), final, 0.7)
    colocar(coro(["F#3", "C#4", "A#4"], 3.0), final, 0.4)
    colocar(timpano(nota("F#2"), 3.0, 1.4), final, 0.9)
    colocar(prato(3.0), final, 0.25)
    colocar(sino(nota("F#4"), 3.0), final, 0.3)

    mix = reverb(mix)
    fade = int(1.2 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade) ** 2
    # Compressão leve para soar mais encorpado
    mix = np.tanh(mix / np.max(np.abs(mix)) * 2.4)
    mix = mix / np.max(np.abs(mix)) * 0.9

    SAIDA_PACOTE.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(SAIDA_PACOTE), mix.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    copia = Path(__file__).parent / "saida"
    copia.mkdir(exist_ok=True)
    sf.write(str(copia / "conquista.wav"), mix.astype(np.float32), SR)
    print(f"{len(mix) / SR:.1f} s gravados em {SAIDA_PACOTE}")


if __name__ == "__main__":
    main()
