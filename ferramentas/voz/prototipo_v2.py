"""Protótipo 2: nomes (de jogadores e mobs) gerados inteiros, como entrariam no resource pack.

Uso: .venv/Scripts/python prototipo_v2.py [faber|cadu]

Nas frases, [colchetes] marcam nomes que viram um áudio inteiro cada. Gera em saida/:
  4_palavras_<n>_<voz>.wav   cada palavra comum é um áudio do banco; nomes inteiros
                             (serve para qualquer texto, inclusive de outros plugins)
  5_trechos_<n>_<voz>.wav    os trechos fixos da frase do config inteiros; nomes inteiros
                             (serve para conquistas configuradas, onde a frase é conhecida)
  1_referencia_<n>_<voz>.wav frase inteira, para comparar
"""

import re
import sys
from pathlib import Path

import numpy as np

from prototipo import Sintetizador, TICK_S, salvar

FRASES = [
    "Alerta, alerta! O boss [Ender Dragon] foi morto. "
    "Seus contribuidores foram [AugustinhoGames], [GeronimoSantos] e [Nonamename].",
    "Alerta! O [Golem Ancião] foi derrotado na masmorra três. "
    "Seus contribuidores foram [xX_Pedro_Xx], [Lulu2009] e [MariaClara].",
]

PAUSA_PALAVRA = 1  # ticks
PAUSA_TRECHO = 2
PAUSA_VIRGULA = 4
PAUSA_PONTO = 7

PASTA = Path(__file__).parent


def texto_falado(nome: str) -> str:
    """Como o nome é lido: AugustinhoGames -> Augustinho Games, xX_Pedro_Xx -> xX Pedro Xx, Lulu2009 -> Lulu 2009."""
    nome = nome.replace("_", " ")
    nome = re.sub(r"(?<=[a-zà-ÿ])(?=[A-Z])", " ", nome)
    nome = re.sub(r"(?<=[A-Za-zÀ-ÿ])(?=\d)|(?<=\d)(?=[A-Za-zÀ-ÿ])", " ", nome)
    return " ".join(nome.split())


def unidades(frase: str, modo: str) -> list:
    """Lista de ('fala', texto) ou ('pausa', ticks)."""
    saida = []
    for parte in re.split(r"(\[[^\]]+\])", frase):
        if not parte.strip():
            continue
        if parte.startswith("["):
            saida.append(("fala", texto_falado(parte[1:-1])))
            saida.append(("pausa", PAUSA_TRECHO if modo == "trechos" else PAUSA_PALAVRA))
            continue
        if modo == "trechos":
            # Trechos fixos entre nomes, quebrados só nas pontuações
            for pedaco in re.findall(r"[^,.!?]+[,.!?]?|[,.!?]", parte):
                fala = pedaco.strip(" ,.!?")
                if fala:
                    saida.append(("fala", pedaco.strip()))
                saida.append(("pausa", pausa_para(pedaco)))
        else:
            for token in re.findall(r"[\wÀ-ÿ]+|[,.!?]", parte):
                if token in ",.!?":
                    saida.append(("pausa", pausa_para(token)))
                else:
                    saida.append(("fala", token.lower()))
                    saida.append(("pausa", PAUSA_PALAVRA))
    return saida


def pausa_para(pedaco: str) -> int:
    fim = pedaco.strip()[-1:] if pedaco.strip() else ""
    if fim == ",":
        return PAUSA_VIRGULA
    if fim in ".!?":
        return PAUSA_PONTO
    return PAUSA_TRECHO


def montar(sint: Sintetizador, lista: list) -> tuple[np.ndarray, list[str]]:
    sr = sint.sr
    tick = int(TICK_S * sr)
    eventos = []
    roteiro = []
    tempo = 0.0
    for tipo, valor in lista:
        if tipo == "pausa":
            tempo += valor
            continue
        audio = sint.falar(valor)
        inicio = round(tempo)
        eventos.append((inicio, audio))
        roteiro.append(f"[{valor}]@{inicio}")
        tempo += len(audio) / tick
    total = max(i * tick + len(a) for i, a in eventos)
    mix = np.zeros(total + sr // 2, dtype=np.float32)
    for inicio, audio in eventos:
        mix[inicio * tick: inicio * tick + len(audio)] += audio
    return mix, roteiro


def main() -> None:
    voz = sys.argv[1] if len(sys.argv) > 1 else "cadu"
    sint = Sintetizador(voz)
    saida = PASTA / "saida"
    saida.mkdir(exist_ok=True)

    for n, frase in enumerate(FRASES, start=1):
        limpa = re.sub(r"\[([^\]]+)\]", lambda m: texto_falado(m.group(1)), frase)
        ref = sint.falar(limpa)
        salvar(saida / f"1_referencia_frase{n}_{voz}.wav", ref, sint.sr)
        print(f"frase {n} referência: {len(ref) / sint.sr:.1f} s")
        for modo, prefixo in (("palavras", "4_palavras"), ("trechos", "5_trechos")):
            audio, roteiro = montar(sint, unidades(frase, modo))
            salvar(saida / f"{prefixo}_frase{n}_{voz}.wav", audio, sint.sr)
            print(f"  {modo}: {len(audio) / sint.sr:.1f} s  " + " ".join(roteiro))


if __name__ == "__main__":
    main()
