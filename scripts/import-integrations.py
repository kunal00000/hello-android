#!/usr/bin/env python3
"""Refresh the bundled Eve directory and its provider artwork (no runtime scraping)."""
import html, json, re
from pathlib import Path
from urllib.request import urlopen

root = Path(__file__).resolve().parents[1]
source = 'https://eve.dev/integrations'
page = urlopen(source).read().decode()
items = {}
assets = root / 'app/src/main/assets/integrations'
assets.mkdir(parents=True, exist_ok=True)
categories = {'Channel': 'channels', 'Connection': 'connectors', 'Extension': 'extensions',
              'Memory provider': 'memory', 'Instrumentation': 'observability'}
def plain(value):
    return html.unescape(re.sub(r'<[^>]+>', '', value)).strip()
for path, card in re.findall(r'<a[^>]*href="(/integrations/[^"?#]+)"[^>]*>(.*?)</a>', page, re.S):
    title = re.search(r'<h3\b[^>]*>(.*?)</h3>', card, re.S)
    if not title or path in items:
        continue
    labels = [plain(s) for s in re.findall(r'<span[^>]*>([^<]*)</span>', card)]
    category = next((categories[s] for s in labels if s in categories), None)
    assert category, path
    slug = path.rsplit('/', 1)[1]
    icon = re.search(r'<svg\b.*?</svg>', card, re.S)
    if icon:
        svg = icon.group().replace('currentColor', '#242423')
        # AndroidSVG does not need web accessibility/class attributes.
        svg = re.sub(r' (?:class|aria-hidden)="[^"]*"', '', svg)
        (assets / f'{slug}.svg').write_text(svg)
    items[path] = dict(id=slug, name=plain(title.group(1)), category=category,
        description=plain(re.search(r'<p\b[^>]*>(.*?)</p>', card, re.S).group(1)),
        url='https://eve.dev'+path, icon=slug if icon else None)
assert len(items) >= 90, 'Unexpected directory response; inspect before replacing the catalog.'
(assets / 'catalog.json').write_text(json.dumps(dict(source=source, items=list(items.values())), indent=2)+'\n')
print(f'Imported {len(items)} integrations')
