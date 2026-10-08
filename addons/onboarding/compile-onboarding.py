"""Compile holylois-onboarding against the live server classpath (read-only). Usage: compile-onboarding.py [BUILD_DIR]
Jars in BUILD_DIR/extra (mods not installed live yet, e.g. Open Parties and Claims) join the classpath."""
import hashlib, json, pathlib, subprocess, sys, zipfile
r = pathlib.Path('/opt/minecraft'); w = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '/home/ubuntu/holylois-build-150')
lib = w/'lib'; lib.mkdir(exist_ok=True)
jars = list((w/'extra').glob('*.jar')) + list((r/'libraries').rglob('*.jar')) + list((r/'versions').rglob('*.jar')) + [m for m in (r/'mods').glob('*.jar') if not m.name.startswith('holylois-onboarding-')]
seen = set()
def nested(p):
    with zipfile.ZipFile(p) as z:
        for n in z.namelist():
            if n.endswith('.jar') and n.startswith('META-INF/jars/'):
                data = z.read(n); h = hashlib.sha256(data).hexdigest(); out = lib/(h + '.jar')
                if h in seen: continue
                seen.add(h); out.write_bytes(data); jars.append(out); nested(out)
for p in list(jars): nested(p)
cp = ':'.join(map(str, jars)); java = '/usr/lib/jvm/java-25-openjdk-arm64/bin/java'
classes = w/'classes'
subprocess.run(['rm', '-rf', str(classes)], check=True); classes.mkdir()
sources = sorted(w.glob('*.java'))
if subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-proc:none', '-Xlint:-options', '-source', '25', '-target', '25',
                   '-cp', cp, '-d', str(classes), *map(str, sources)]).returncode: raise SystemExit('javac failed')
# Every *Test.java is a main-method test (OnboardingTest, AccountRequestsTest, ...): all must pass, none ship.
tests = sorted(p.stem for p in sources if p.stem.endswith('Test'))
for test in tests:
    if subprocess.run([java, '-cp', str(classes) + ':' + cp, 'holylois.' + test]).returncode: raise SystemExit(test + ' failed')
print('tests passed:', ', '.join(tests))
meta = json.loads((w/'fabric.mod.json').read_text())
out = w/f"holylois-onboarding-{meta['version']}+26.3.jar"
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('fabric.mod.json', json.dumps(meta, indent=2))
    z.write(w/'holylois-onboarding.mixins.json', 'holylois-onboarding.mixins.json')
    z.write(w/'holylois-namedays-lv.txt', 'holylois-namedays-lv.txt')
    z.write(w/'icon.png', 'icon.png')
    for p in sorted(classes.rglob('*.class')):
        if p.name.split('.')[0].split('$')[0] in tests: continue
        z.write(p, p.relative_to(classes).as_posix())
    for p in sources:
        if p.stem not in tests: z.write(p, 'src/' + p.name)
print(json.dumps({'jar': str(out), 'sha256': hashlib.sha256(out.read_bytes()).hexdigest(), 'size': out.stat().st_size}))
