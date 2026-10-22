"""Simula o anúncio completo (batidas + voz + música) para ouvir cada narrador fora do jogo.

Uso: .venv/Scripts/python demo_anuncio.py
Gera saida/demo/demo_<narrador>.wav com os mesmos tempos e pausas do plugin.
"""

import argparse
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

PASTA = Path(__file__).parent
RAIZ = PASTA.parents[1]
sys.path.insert(0, str(RAIZ / "src" / "main" / "resources" / "voz"))
from gerar_voz import aparar, motor_kokoro, motor_piper, texto_falado  # noqa: E402

SR = 32000
TICK = 0.05
# Mesmos valores do plugin: sino.ritmo, musica.inicio-ticks 1 e voz.atraso-ticks 20
RITMO = [17, 27, 37, 46, 53, 60, 66]
BATIDAS_S = [t * TICK for t in RITMO]
INICIO_FALA_S = (RITMO[-1] + 20) * TICK
INICIO_MUSICA_S = 1 * TICK
PAUSA = {"virgula": 4, "ponto": 7, "trecho": 2, "palavra": 1}
VOLUME_MUSICA = 0.45

NARRADORES = {
    "cadu": dict(motor="piper", modelo="vozes/pt_BR-cadu-medium.onnx"),
    "alex": dict(motor="kokoro", voz="pm_alex"),
    "dora": dict(motor="kokoro", voz="pf_dora"),
    "clara": dict(motor="kokoro", voz="pf_dora:0.6,if_sara:0.4"),
    "sol": dict(motor="kokoro", voz="pf_dora:0.5,pm_alex:0.5"),
}

# (texto, tipo, pausa depois) seguindo a frase padrão com {final}
ROTEIRO = [
    ("Alerta,", "texto", "virgula"), ("alerta!", "texto", "ponto"),
    ("O boss Ender Dragon foi morto", "texto", "trecho"), (None, None, "ponto"),
    ("Seus contribuidores foram", "texto", "trecho"),
    ("AugustinhoGames", "nome", "virgula"), ("GeronimoSantos", "nome", "palavra"),
    ("e", "texto", "palavra"), ("Nonamename", "nome", "trecho"), (None, None, "ponto"),
    ("Essa conquista vai causar grande impacto no mundo.", "texto", "trecho"),
]


def reamostrar(audio, de, para):
    if de == para:
        return audio
    n = int(len(audio) * para / de)
    return np.interp(np.linspace(0, len(audio) - 1, n), np.arange(len(audio)), audio)


def dong():
    """Batida de sino sintetizada (só para a demonstração; no jogo é o som do sino do Minecraft)."""
    t = np.arange(int(2.5 * SR)) / SR
    f = 330
    parciais = [(1.0, 1, 1.2), (0.6, 2.0, 2.0), (0.4, 2.76, 2.5), (0.25, 5.4, 4.0), (0.3, 0.5, 0.9)]
    som = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t * d) for a, r, d in parciais)
    return som * np.minimum(1, t / 0.002) * 0.5


def main():
    musica, sr_m = sf.read(str(RAIZ / "src/main/resources/pacote/assets/sinos/sounds/musica/conquista.ogg"))
    musica = reamostrar(musica, sr_m, SR)
    saida = PASTA / "saida" / "demo"
    saida.mkdir(parents=True, exist_ok=True)

    for nome, cfg in NARRADORES.items():
        opcoes = argparse.Namespace(modelo=str(PASTA / cfg.get("modelo", "vozes/kokoro-v1.0.int8.onnx")),
                                    vozes=str(PASTA / "vozes/voices-v1.0.bin"), voz=cfg.get("voz", ""), velocidade=1.0)
        falar = motor_kokoro(opcoes) if cfg["motor"] == "kokoro" else motor_piper(opcoes)

        mix = np.zeros(int(20 * SR))

        def colocar(som, inicio_s, volume=1.0):
            i = int(inicio_s * SR)
            fim = min(len(mix), i + len(som))
            mix[i:fim] += som[: fim - i] * volume

        for b in BATIDAS_S:
            colocar(dong(), b, 0.8)
        colocar(musica, INICIO_MUSICA_S, VOLUME_MUSICA)

        tempo = 0.0  # ticks desde o início da fala
        for texto, tipo, pausa in ROTEIRO:
            if texto:
                audio, sr = falar(texto_falado(texto) if tipo == "nome" else texto)
                audio = aparar(audio, sr)
                audio = reamostrar(audio / np.max(np.abs(audio)) * 0.9, sr, SR)
                colocar(audio, INICIO_FALA_S + round(tempo) * TICK)
                tempo += len(audio) / SR / TICK
            tempo += PAUSA[pausa]

        fim = int((INICIO_FALA_S + tempo * TICK + 2.5) * SR)
        mix = mix[:fim]
        mix = mix / np.max(np.abs(mix)) * 0.9
        sf.write(str(saida / f"demo_{nome}.wav"), mix.astype(np.float32), SR)
        print(nome, f"{len(mix) / SR:.1f} s")


if __name__ == "__main__":
    main()
