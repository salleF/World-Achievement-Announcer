"""Gera previa_animacao.gif: o balanço do sino e do badalo como o plugin faz (vista de frente).

Uso: ../voz/.venv/Scripts/python previa_animacao.py
Lê o modelo e a textura do pacote e repete a lógica de SwingTimeline (3 batidas, 8 ticks).
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "pacote" / "assets" / "sinos"
BATIDAS, INTERVALO, PARTE_SINO = 3, 8, 0.6

modelo = json.loads((RAIZ / "models" / "item" / "sino.json").read_text(encoding="utf-8"))
textura = Image.open(RAIZ / "textures" / "item" / "sino.png").convert("RGB")
tw, th = textura.size
corpo = [e for e in modelo["elements"] if not e.get("name", "").lower().startswith("badalo")]
badalo = [e for e in modelo["elements"] if e.get("name", "").lower().startswith("badalo")]

# Mesma geometria de BellModel
pivo = max(e["to"][1] for e in corpo)
bola = min(badalo, key=lambda e: e["from"][1])
borda = min(corpo, key=lambda e: e["from"][1])
raio = (bola["to"][0] - bola["from"][0]) / 2
comprimento = pivo - (bola["from"][1] + bola["to"][1]) / 2
contato = math.degrees(math.asin(min(1, ((borda["to"][0] - borda["from"][0]) / 2 - raio) / comprimento)))

# Mesma linha do tempo de SwingTimeline
amp_s, amp_b = contato * PARTE_SINO, contato * (1 - PARTE_SINO)
quadros = [(0, 0, 0, False)]
t, lado = max(3, INTERVALO * 2 // 3), 1
for _ in range(BATIDAS):
    quadros.append((t, lado * amp_s, -lado * amp_b, True)); t += INTERVALO; lado = -lado
quadros.append((t, lado * amp_s * .45, -lado * amp_b * .45, False)); t += INTERVALO; lado = -lado
quadros.append((t, lado * amp_s * .15, -lado * amp_b * .15, False)); t += max(2, INTERVALO // 2)
quadros.append((t, 0, 0, False))

S, W, H = 16, 340, 360
cx, cy = W // 2, 60  # posição do pivô na imagem


def desenhar(img, elementos, angulo):
    d = ImageDraw.Draw(img)
    a = math.radians(angulo)
    for e in elementos:
        (x0, y0, _), (x1, y1, _) = e["from"], e["to"]
        uv = e["faces"]["north"]["uv"]
        n = 6
        for i in range(n):  # faixas horizontais para mostrar o gradiente da textura
            ya, yb = y0 + (y1 - y0) * i / n, y0 + (y1 - y0) * (i + 1) / n
            v = uv[1] + (uv[3] - uv[1]) * (1 - (i + .5) / n)
            u = (uv[0] + uv[2]) / 2
            cor = textura.getpixel((min(tw - 1, int(u / 16 * tw)), min(th - 1, int(v / 16 * th))))
            pts = []
            for px, py in ((x0, ya), (x1, ya), (x1, yb), (x0, yb)):
                dx, dy = px - 8, py - pivo
                rx = dx * math.cos(a) - dy * math.sin(a)
                ry = dx * math.sin(a) + dy * math.cos(a)
                pts.append((cx + rx * S, cy - ry * S))
            d.polygon(pts, fill=cor)


imagens = []
fim = quadros[-1][0] + 6
for tick in range(fim + 1):
    ant = max((q for q in quadros if q[0] <= tick), key=lambda q: q[0])
    prox = min((q for q in quadros if q[0] >= tick), key=lambda q: q[0], default=quadros[-1])
    f = 0 if prox[0] == ant[0] else (tick - ant[0]) / (prox[0] - ant[0])
    ang_s = ant[1] + (prox[1] - ant[1]) * f
    ang_b = ant[2] + (prox[2] - ant[2]) * f
    img = Image.new("RGB", (W, H), (36, 40, 48))
    desenhar(img, badalo, ang_b)
    desenhar(img, corpo, ang_s)
    d = ImageDraw.Draw(img)
    d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=(230, 80, 80))
    if any(q[0] == tick and q[3] for q in quadros):
        d.text((12, 12), "DONG!", fill=(255, 230, 120))
    d.text((12, H - 22), f"tick {tick}  sino {ang_s:+.1f}  badalo {ang_b:+.1f}", fill=(200, 200, 200))
    imagens.append(img)

imagens[0].save(Path(__file__).parent / "previa_animacao.gif", save_all=True, append_images=imagens[1:],
                duration=50, loop=0)
print(f"contato {contato:.1f} graus, {len(imagens)} quadros, batidas nos ticks {[q[0] for q in quadros if q[3]]}")
