#!/usr/bin/env python3
"""Monta um mosaico 2 colunas das screenshots do teste visual. Uso: contact_sheet.py <saida.png> nome1 nome2 ..."""
import sys
from PIL import Image
out, names = sys.argv[1], sys.argv[2:]
ims = [Image.open(f"build/visualtest/vt_{n}.png").convert("RGB").resize((640, 360)) for n in names]
rows = (len(ims) + 1) // 2
sheet = Image.new("RGB", (1280, 360 * rows))
for i, im in enumerate(ims):
    sheet.paste(im, ((i % 2) * 640, (i // 2) * 360))
sheet.save(out, optimize=True)
