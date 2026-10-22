"""Prepara músicas próprias para o SinosDoMundo.

O plugin chama este script como um programa separado:
  python musica.py analisar <arquivo>
      -> {"duracao": 6.72, "ataques": [[0.79, 0.62], ...], "fala": 4.27}
         ataques = [segundos, força de 0 a 1]; fala = momento sugerido para a voz começar
  python musica.py converter <arquivo> <saida.ogg> [--estender SEGUNDOS]
      -> {"duracao": 17.5}
         converte para OGG mono com o volume ajustado; com --estender, continua a música com um
         fundo baixo feito do próprio som dela até completar SEGUNDOS

Aceita .ogg, .mp3, .wav e .flac. Requisitos: pip install soundfile numpy
"""

import argparse
import json
import sys

import numpy as np
import soundfile as sf


def carregar(caminho):
    audio, sr = sf.read(caminho)
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    pico = np.max(np.abs(audio)) if audio.size else 0
    if pico > 0:
        audio = audio / pico * 0.9
    return audio.astype(np.float64), sr


def fluxo_espectral(audio, sr, passo_s=0.01, janela=1024):
    """Quanto o espectro "cresce" de um quadro para o outro: picos = ataques."""
    passo = max(1, int(sr * passo_s))
    hann = np.hanning(janela)
    quadros = [np.abs(np.fft.rfft(audio[i:i + janela] * hann)) for i in range(0, len(audio) - janela, passo)]
    if len(quadros) < 3:
        return np.zeros(1), passo_s
    log = np.log1p(np.array(quadros) * 10)
    fluxo = np.maximum(0, np.diff(log, axis=0)).sum(axis=1)
    return np.convolve(fluxo, np.ones(3) / 3, "same"), passo_s


def ataques(audio, sr):
    fluxo, passo = fluxo_espectral(audio, sr)
    if fluxo.max() <= 0:
        return []
    limite = fluxo.mean() + 1.2 * fluxo.std()
    picos = []
    for i in range(5, len(fluxo) - 5):
        if fluxo[i] > limite and fluxo[i] == fluxo[i - 5:i + 6].max():
            picos.append([round((i + 1) * passo, 3), round(float(fluxo[i] / fluxo.max()), 3)])
    return picos


def analisar(caminho):
    audio, sr = carregar(caminho)
    lista = ataques(audio, sr)
    fortes = [t for t, forca in lista if forca >= 0.55]
    fala = (fortes[-1] + 1.0) if fortes else min(2.0, len(audio) / sr)
    return {"duracao": round(len(audio) / sr, 3), "ataques": lista, "fala": round(fala, 3)}


def trecho_estavel(audio, sr, dur=0.7):
    """Janela da segunda metade com menos ataques e volume razoável (o acorde que fica soando)."""
    fluxo, passo = fluxo_espectral(audio, sr, 0.05, 2048)
    n = int(dur / passo)
    rms = np.array([np.sqrt(np.mean(audio[i:i + 2048] ** 2)) for i in range(0, len(audio) - 2048, int(sr * passo))])
    m = min(len(fluxo), len(rms))
    melhor, nota = None, None
    for i in range(int(m * 0.4), m - n):
        if rms[i:i + n].mean() < 0.35 * rms.max():
            continue
        valor = fluxo[i:i + n].mean()
        if nota is None or valor < nota:
            melhor, nota = i, valor
    if melhor is None:
        melhor = max(0, m - n - 1)
    inicio = melhor * passo
    return audio[int(inicio * sr): int((inicio + dur) * sr)]


def granular(trecho, sr, duracao, rng):
    grao = min(int(0.35 * sr), len(trecho) - 1)
    passo = max(1, grao // 4)
    janela = np.hanning(grao)
    saida = np.zeros(int(duracao * sr) + grao)
    for i in range(0, len(saida) - grao, passo):
        inicio = rng.integers(0, max(1, len(trecho) - grao))
        saida[i:i + grao] += trecho[inicio:inicio + grao] * janela * rng.uniform(0.85, 1.0)
    return saida[: int(duracao * sr)]


def passa_baixa(x, sr, corte):
    espectro = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / sr)
    return np.fft.irfft(espectro / (1 + (f / corte) ** 4), len(x))


def estender(audio, sr, total):
    """Mesmo processo usado na música padrão: fundo granular do acorde final + sinos da própria música."""
    rng = np.random.default_rng(11)
    dur = len(audio) / sr
    mix = np.zeros(int(total * sr))
    mix[: len(audio)] += audio

    entrada = max(0.5, dur - 2.4)
    fundo = passa_baixa(granular(trecho_estavel(audio, sr), sr, total - entrada, rng), sr, 1800)
    rms = np.sqrt(np.mean(fundo ** 2))
    if rms > 0:
        fundo *= 0.045 / rms
    t = np.arange(len(fundo)) / sr
    fundo *= np.clip(t / 1.5, 0, 1) ** 2
    mix[int(entrada * sr):int(entrada * sr) + len(fundo)] += fundo[: len(mix) - int(entrada * sr)]

    # Sino: o ataque mais forte da segunda metade, repetido de leve
    lista = [a for a in ataques(audio, sr) if a[0] > dur * 0.4]
    if lista:
        t_sino = max(lista, key=lambda a: a[1])[0]
        sino = audio[int(t_sino * sr): int((t_sino + 0.7) * sr)].copy()
        if sino.size:
            sino *= np.linspace(1, 0, len(sino)) ** 1.5
            sino = passa_baixa(sino, sr, 4000)
            sino /= np.max(np.abs(sino)) or 1
            vez = 0
            for momento in np.arange(entrada + 1.9, total - 2.5, 2.2):
                som = np.interp(np.arange(0, len(sino), 0.5), np.arange(len(sino)), sino) if vez % 2 else sino
                i = int(momento * sr)
                fim = min(len(mix), i + len(som))
                mix[i:fim] += som[: fim - i] * 0.22 * (0.8 if vez % 2 else 1.0)
                vez += 1

    n = int(2.0 * sr)
    mix[-n:] *= np.linspace(1, 0, n) ** 2
    return mix / np.max(np.abs(mix)) * 0.9


def gravar_ogg(caminho, audio, sr):
    # Em blocos: gravar um OGG longo de uma vez estoura a pilha da libsndfile no Windows
    with sf.SoundFile(caminho, "w", sr, 1, format="OGG", subtype="VORBIS") as ogg:
        for i in range(0, len(audio), sr):
            ogg.write(audio[i:i + sr].astype(np.float32))


def main():
    args = argparse.ArgumentParser()
    args.add_argument("comando", choices=["analisar", "converter"])
    args.add_argument("arquivo")
    args.add_argument("saida", nargs="?")
    args.add_argument("--estender", type=float, default=0)
    opcoes = args.parse_args()

    if opcoes.comando == "analisar":
        resultado = analisar(opcoes.arquivo)
    else:
        audio, sr = carregar(opcoes.arquivo)
        if opcoes.estender > len(audio) / sr + 1:
            audio = estender(audio, sr, opcoes.estender)
        gravar_ogg(opcoes.saida, audio, sr)
        resultado = {"duracao": round(len(audio) / sr, 3)}
    sys.stdout.write(json.dumps(resultado))
    sys.stdout.flush()


if __name__ == "__main__":
    main()
