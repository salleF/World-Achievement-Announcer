"""Lista os trechos de texto em português do código Java (o que o jogador e o admin veem).

Uso: python extrair_textos.py [lang/en.yml]
Imprime os trechos que ainda não têm tradução no arquivo de idioma informado (ou todos).
Cada trecho é uma string literal do código: é ela que o plugin procura e troca pela tradução.
"""

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2] / "src" / "main" / "java" / "br" / "sinosdomundo"
# Arquivos sem texto para o usuário (ou com texto falado, tratado à parte)
IGNORAR = {"SpeechPlanner.java", "BellGrouper.java", "ContributorLedger.java", "TriggerListener.java",
           "MenuListener.java", "Conquest.java", "BellModel.java", "SwingTimeline.java", "Lang.java"}
LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
# Chaves de config, ids, caminhos, sons e código Python não são texto para traduzir
TECNICO = re.compile(r"^(sinos|minecraft|voz|sino|texto|pacote|musica|contribuidores|conquistas|assets|pacote|scripts|"
                     r"lang|vozes|python-env|plugins|[a-z_]+\.[a-z]+)[.:/]|^[a-z0-9_.:/-]+$|^\{|^<[a-z_]+>$|"
                     r"^import |^r=|^\[r\.|^print\(|importlib|^--|^[A-Z_]+$|^%")


def literais():
    vistos = []
    for arquivo in sorted(RAIZ.rglob("*.java")):
        if arquivo.name in IGNORAR:
            continue
        for linha in arquivo.read_text(encoding="utf-8").splitlines():
            codigo = linha.strip()
            if codigo.startswith(("//", "*", "/*", "@", "import ", "package ")):
                continue
            for m in LITERAL.finditer(linha):
                texto = bytes(m.group(1), "utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8")
                limpo = re.sub(r"<[^>]+>", "", texto).strip()
                if len(re.findall(r"[A-Za-zÀ-ÿ]", limpo)) < 3 or TECNICO.search(texto.strip()):
                    continue
                if texto not in vistos:
                    vistos.append(texto)
    return vistos


def main():
    existentes = set()
    if len(sys.argv) > 1 and Path(sys.argv[1]).exists():
        for linha in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
            m = re.match(r'^\s*- pt: "((?:[^"\\]|\\.)*)"', linha)
            if m:
                existentes.add(m.group(1).replace('\\"', '"').replace("\\\\", "\\"))
    faltam = [t for t in literais() if t not in existentes]
    for t in faltam:
        print(t)
    print(f"# {len(faltam)} trechos", file=sys.stderr)


if __name__ == "__main__":
    main()
