"""Download and verify every jar in deps.lock.json into addons/.deps, then unpack nested jars (Fabric jar-in-jar).

Usage: python addons/fetch-deps.py   (cached: a jar is downloaded only when missing or its hash differs)
Gradle compiles against .deps/*.jar and .deps/nested/*.jar, the same bytes the pack and server ship.
"""
import hashlib, json, sys, urllib.request, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEPS = HERE / '.deps'
NESTED = DEPS / 'nested'
lock = json.loads((HERE / 'deps.lock.json').read_text(encoding='utf-8'))['jars']
DEPS.mkdir(exist_ok=True); NESTED.mkdir(exist_ok=True)


def sha256(path): return hashlib.sha256(path.read_bytes()).hexdigest()


fetched = 0
for name, entry in lock.items():
    target = DEPS / name
    if target.exists() and sha256(target) == entry['sha256']: continue
    request = urllib.request.Request(entry['url'], headers={'User-Agent': 'pjampjam/holylois-addons-build'})
    data = urllib.request.urlopen(request, timeout=120).read()
    if hashlib.sha256(data).hexdigest() != entry['sha256']: sys.exit(f'Hash mismatch for {name}; refusing to use it.')
    target.write_bytes(data); fetched += 1
for stale in DEPS.glob('*.jar'):
    if stale.name not in lock: stale.unlink()

seen = set()
def unpack(path):
    with zipfile.ZipFile(path) as jar:
        for inner in jar.namelist():
            if inner.startswith('META-INF/jars/') and inner.endswith('.jar'):
                data = jar.read(inner); digest = hashlib.sha256(data).hexdigest()
                if digest in seen: continue
                seen.add(digest); out = NESTED / (digest[:16] + '-' + Path(inner).name)
                if not out.exists(): out.write_bytes(data)
                unpack(out)
for jar in sorted(DEPS.glob('*.jar')): unpack(jar)
for stale in NESTED.glob('*.jar'):
    if stale.name.split('-', 1)[0] not in {d[:16] for d in seen}: stale.unlink()
print(f'{len(lock)} locked jars ({fetched} downloaded), {len(seen)} nested jars ready in {DEPS}')
