"""Builds docs/ui-audit/2026-10-28-ui12/compare/*.png: BEFORE | AFTER (| owner-approved mock-up where one exists), same synthetic data,
language, size, font and theme. Usage: python compose_compare.py <dir containing before/ and after/> """
import sys
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(sys.argv[1])
mock = Path(__file__).resolve().parents[2] / 'design/ui12-concentration-2026-10-28/assets'
MOCK = {'mixed': '01-浓度估算-默认3天.png', 'tap_readout': '02-浓度估算-7天-点按读数.png', 'pmol': '03-浓度估算-pmol.png'}
out = root / 'compare'; out.mkdir(exist_ok=True)
H = 1500

def fit(img, h=H):
    return img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)

def label(img, text):
    pad = 36; canvas = Image.new('RGB', (img.width, img.height + pad), 'white'); canvas.paste(img.convert('RGB'), (0, pad))
    ImageDraw.Draw(canvas).text((8, 10), text, fill='black'); return canvas

for after in sorted((root / 'after').glob('*_en_411dp_1x_light.png')) + sorted((root / 'after').glob('*_zh-rCN_411dp_1x_light.png')) + sorted((root / 'after').glob('*_en_320dp_2x_light.png')):
    name = after.name; state = name.split('_en_')[0].split('_zh-rCN_')[0]
    before = root / 'before' / name
    if not before.exists():
        continue
    tiles = [label(fit(Image.open(before)), 'BEFORE f059c57d'), label(fit(Image.open(after)), 'AFTER UI-12')]
    if state in MOCK and '_411dp_' in name and '_en_' in name:
        tiles.append(label(fit(Image.open(mock / MOCK[state])), 'Owner-approved mock-up (fictional data)'))
    w = sum(t.width for t in tiles) + 20 * (len(tiles) - 1)
    sheet = Image.new('RGB', (w, tiles[0].height), 'white'); x = 0
    for t in tiles:
        sheet.paste(t, (x, 0)); x += t.width + 20
    sheet.save(out / name)
print(len(list(out.glob('*.png'))), 'comparison sheets')
