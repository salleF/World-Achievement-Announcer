"""Gera o modelo 3D do sino (cubos, formato de modelo de item do Minecraft Java) e a textura.

Uso: python gerar_sino.py
Escreve em src/main/resources/pacote/assets/sinos/:
  items/sino.json, models/item/sino.json, textures/item/sino.png

O modelo pode ser aberto e melhorado no Blockbench (File > Open Model).
"""

import json
import struct
import zlib
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "pacote" / "assets" / "sinos"

# Textura 32x32 dividida em 3 regiões de 16x16 texels (8x8 em unidades de UV)
OURO_CLARO = (247, 216, 112)
OURO = (218, 172, 62)
OURO_ESCURO = (150, 104, 30)
FAIXA = (112, 74, 20)
FERRO = (88, 88, 96)
FERRO_ESCURO = (52, 52, 58)
INTERIOR = (60, 38, 10)


def ruido(x: int, y: int) -> float:
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n & 0xFF) / 255 - 0.5) * 0.08


def misturar(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def escurecer(cor, f):
    return tuple(max(0, min(255, round(c * (1 + f)))) for c in cor)


def pixel(x: int, y: int):
    if y < 16 and x < 16:
        # Laterais: gradiente de cima (claro) para baixo (escuro), duas faixas e um brilho vertical
        cor = misturar(OURO_CLARO, OURO_ESCURO, y / 15)
        if y in (3, 12):
            cor = FAIXA
        elif 3 <= x <= 4:
            cor = misturar(cor, (255, 245, 200), 0.45)
        return escurecer(cor, ruido(x, y))
    if y < 16:
        # Topos: ouro claro com um anel
        dx, dy = x - 23.5, y - 7.5
        d = (dx * dx + dy * dy) ** 0.5
        cor = OURO_ESCURO if 5.5 < d < 6.5 else OURO
        return escurecer(cor, ruido(x, y))
    if x < 16:
        # Badalo: ferro
        cor = misturar(FERRO, FERRO_ESCURO, (y - 16) / 15)
        return escurecer(cor, ruido(x, y))
    # Interior (fundo da boca do sino)
    return escurecer(INTERIOR, ruido(x, y))


def salvar_png(caminho: Path, largura: int, altura: int) -> None:
    linhas = b""
    for y in range(altura):
        linhas += b"\x00" + b"".join(bytes(pixel(x, y)) + b"\xff" for x in range(largura))

    def bloco(tipo, dados):
        return struct.pack(">I", len(dados)) + tipo + dados + struct.pack(">I", zlib.crc32(tipo + dados) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += bloco(b"IHDR", struct.pack(">IIBBBBB", largura, altura, 8, 6, 0, 0, 0))
    png += bloco(b"IDAT", zlib.compress(linhas, 9))
    png += bloco(b"IEND", b"")
    caminho.write_bytes(png)


UV_LATERAL = (0, 0, 8, 8)
UV_TOPO = (8, 0, 16, 8)
UV_FERRO = (0, 8, 8, 16)
UV_INTERIOR = (8, 8, 16, 16)

# (nome, meia largura, y inicial, y final, material)
PECAS = [
    ("coroa", 1.0, 14.0, 16.0, "ouro"),
    ("ombro", 3.0, 12.5, 14.0, "ouro"),
    ("corpo1", 3.5, 10.0, 12.5, "ouro"),
    ("corpo2", 4.0, 7.0, 10.0, "ouro"),
    ("corpo3", 4.5, 4.5, 7.0, "ouro"),
    ("saia", 5.25, 3.0, 4.5, "ouro"),
    ("borda", 5.75, 2.0, 3.0, "ouro"),
    ("badalo", 0.9, 0.3, 2.0, "ferro"),
]


def elemento(nome, meia, y0, y1, material):
    de = [8 - meia, y0, 8 - meia]
    ate = [8 + meia, y1, 8 + meia]
    if material == "ferro":
        lateral = topo = baixo = list(UV_FERRO)
    else:
        u0, v0, u1, v1 = UV_LATERAL
        # Cada peça pega a faixa do gradiente que corresponde à sua altura
        lateral = [u0, round((16 - y1) / 2, 3), u1, round((16 - y0) / 2, 3)]
        topo = list(UV_TOPO)
        baixo = list(UV_INTERIOR)
    faces = {lado: {"uv": lateral, "texture": "#0"} for lado in ("north", "east", "south", "west")}
    faces["up"] = {"uv": topo, "texture": "#0"}
    faces["down"] = {"uv": baixo, "texture": "#0"}
    return {"name": nome, "from": de, "to": ate, "faces": faces}


def main() -> None:
    (RAIZ / "items").mkdir(parents=True, exist_ok=True)
    (RAIZ / "models" / "item").mkdir(parents=True, exist_ok=True)
    (RAIZ / "textures" / "item").mkdir(parents=True, exist_ok=True)

    modelo = {
        "textures": {"0": "sinos:item/sino", "particle": "sinos:item/sino"},
        "elements": [elemento(*p) for p in PECAS],
        "display": {
            "gui": {"rotation": [25, 45, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }
    (RAIZ / "models" / "item" / "sino.json").write_text(json.dumps(modelo, indent=2), encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": "sinos:item/sino"}}
    (RAIZ / "items" / "sino.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
    salvar_png(RAIZ / "textures" / "item" / "sino.png", 32, 32)
    print("Modelo gerado em", RAIZ)


if __name__ == "__main__":
    main()
