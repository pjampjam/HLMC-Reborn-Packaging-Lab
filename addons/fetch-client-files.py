"""Give the local test client what players get besides mods: the pack's shaderpacks and resource packs (from the signed
assets/pack.json, sha256-checked) and the shipped settings (assets/defaults.zip, applied by YOSBR on the first start).

Usage: python fetch-client-files.py [run/client|run/gametest]   (default run/client; existing files with the right hash are kept, settings never overwrite
what the test client already changed).
"""
import hashlib, json, sys, urllib.request, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent / 'assets'
CLIENT = HERE / (sys.argv[1] if len(sys.argv) > 1 else 'run/client')  # e.g. run/gametest


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''): digest.update(block)
    return digest.hexdigest()


fetched = kept = 0
for entry in json.loads((ASSETS / 'pack.json').read_text(encoding='utf-8'))['files']:
    if not entry['path'].startswith(('shaderpacks/', 'resourcepacks/')): continue
    target = CLIENT / entry['path']
    if target.exists() and target.stat().st_size == entry['size'] and sha256(target) == entry['sha256']: kept += 1; continue
    target.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'holylois-test-client'})
    data = urllib.request.urlopen(request, timeout=300).read()
    if hashlib.sha256(data).hexdigest() != entry['sha256']: raise SystemExit(f'Hash mismatch for {entry["path"]}')
    target.write_bytes(data); fetched += 1

settings = 0
with zipfile.ZipFile(ASSETS / 'defaults.zip') as defaults:
    for name in defaults.namelist():
        target = CLIENT / name
        if name.endswith('/') or target.exists() or '..' in Path(name).parts: continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(defaults.read(name)); settings += 1
print(f'Test client: {fetched} packs downloaded, {kept} already there, {settings} shipped settings files added in {CLIENT}')
