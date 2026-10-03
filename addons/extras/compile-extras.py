"""Build holylois-boombox on the server against the live classpath (read-only). Run from ~/holylois-boombox."""
import hashlib, io, json, pathlib, subprocess, urllib.request, zipfile
r = pathlib.Path('/opt/minecraft'); w = pathlib.Path(__file__).resolve().parent
lib = w / 'lib'; lib.mkdir(exist_ok=True)
JLAYER = 'https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar'
jl = lib / 'jlayer-1.0.1.jar'
if not jl.exists(): jl.write_bytes(urllib.request.urlopen(JLAYER, timeout=60).read())
sha1 = hashlib.sha1(jl.read_bytes()).hexdigest()
expected = urllib.request.urlopen(JLAYER + '.sha1', timeout=30).read().decode().split()[0]
assert sha1 == expected, ('jlayer checksum', sha1, expected)
jars = list((r / 'libraries').rglob('*.jar')) + list((r / 'versions').rglob('*.jar')) + list((r / 'mods').glob('*.jar')) + [jl, pathlib.Path('/home/ubuntu/holylois-client/minecraft-26.3-client.jar')]
seen = set()
def nested(p):
    with zipfile.ZipFile(p) as z:
        for n in z.namelist():
            if n.endswith('.jar') and n.startswith('META-INF/jars/'):
                data = z.read(n); h = hashlib.sha256(data).hexdigest(); out = lib / (h + '.jar')
                if h in seen: continue
                seen.add(h); out.write_bytes(data); jars.append(out); nested(out)
for p in list(jars): nested(p)
cp = ':'.join(map(str, jars)); java = '/usr/lib/jvm/java-25-openjdk-arm64/bin/java'
classes = w / 'classes'
subprocess.run(['rm', '-rf', str(classes)], check=True); classes.mkdir()
sources = sorted(w.glob('*.java'))
if subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-proc:none', '-Xlint:-options', '-source', '25', '-target', '25',
                   '-cp', cp, '-d', str(classes), *map(str, sources)]).returncode: raise SystemExit('javac failed')
if subprocess.run([java, '-cp', str(classes) + ':' + cp, 'holylois.boombox.BoomboxTest', '8']).returncode: raise SystemExit('BoomboxTest failed')
# Fabric only loads nested jars that carry their own fabric.mod.json, so JLayer gets a small wrapper.
wrapped = io.BytesIO()
with zipfile.ZipFile(jl) as src, zipfile.ZipFile(wrapped, 'w', zipfile.ZIP_DEFLATED) as dst:
    for info in src.infolist():
        if not info.filename.upper().startswith('META-INF/') or info.filename.upper().startswith('META-INF/LICENSE'): dst.writestr(info, src.read(info))
    dst.writestr('fabric.mod.json', json.dumps({"schemaVersion": 1, "id": "jlayer", "version": "1.0.1", "name": "JLayer (javazoom, LGPL-2.1)", "license": "LGPL-2.1"}))
meta = json.loads((w / 'fabric.mod.json').read_text())
out = w / f"holylois-extras-{meta['version']}+26.3.jar"
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('fabric.mod.json', json.dumps(meta, indent=2))
    z.write(w / 'holylois-extras.mixins.json', 'holylois-extras.mixins.json')
    z.writestr('META-INF/jars/jlayer-1.0.1-holylois.jar', wrapped.getvalue())
    for folder in ['assets', 'data']:
        for p in sorted((w / folder).rglob('*')):
            if p.is_file(): z.write(p, p.relative_to(w).as_posix())
    for p in sorted(classes.rglob('*.class')):
        if not p.name.startswith('BoomboxTest'): z.write(p, p.relative_to(classes).as_posix())
    for p in sources:
        if p.name != 'BoomboxTest.java': z.write(p, 'src/' + p.name)
    z.write(w / 'README.md', 'README.md')
print(json.dumps({'jar': str(out), 'sha256': hashlib.sha256(out.read_bytes()).hexdigest(), 'size': out.stat().st_size}))
