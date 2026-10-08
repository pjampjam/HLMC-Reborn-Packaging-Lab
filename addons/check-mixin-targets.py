"""Static mixin target check: every target class and every injected method name must exist in the jars we ship.

Extras uses defaultRequire 0, so a renamed target fails silently at runtime (the 1.7.5 tool-swap fix never ran because of
that). This catches it at build time. Usage: python check-mixin-targets.py [MINECRAFT_JAR]   (needs javap on PATH or JAVA_HOME)
Exit code 1 lists every missing class or method.
"""
import json, os, re, shutil, subprocess, sys, zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ADDONS = ['extras', 'onboarding', 'auth-ui']


def minecraft_jar():
    if len(sys.argv) > 1: return Path(sys.argv[1])
    found = sorted((Path.home() / '.gradle/caches/fabric-loom').glob('26.3/minecraft-merged.jar'))
    if not found: sys.exit('Run gradlew build first (Loom downloads Minecraft 26.3), or pass the merged jar path.')
    return found[0]


def javap():
    home = os.environ.get('JAVA_HOME')
    exe = Path(home) / 'bin' / ('javap.exe' if os.name == 'nt' else 'javap') if home else None
    return str(exe) if exe and exe.exists() else shutil.which('javap') or sys.exit('javap not found (set JAVA_HOME)')


jars = [minecraft_jar(), *sorted((HERE / '.deps').glob('*.jar')), *sorted((HERE / '.deps/nested').glob('*.jar'))]
index = {}
for jar in jars:
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith('.class'): index.setdefault(name[:-6].replace('/', '.'), jar)
# Our own classes (shared payloads/screens) also count as targets.
own = {}
for addon in ADDONS:
    for source in (HERE / addon).glob('*.java'):
        package = re.search(r'package\s+([\w.]+);', source.read_text(encoding='utf-8'))
        if package: own[package.group(1) + '.' + source.stem] = source

JAVAP = javap()
members_cache = {}


def members(cls):
    if cls not in members_cache:
        out = subprocess.run([JAVAP, '-p', '-cp', str(index[cls]), cls], capture_output=True, text=True).stdout
        names = {name.rsplit('.', 1)[-1] for name in re.findall(r'(?:^|\s)([\w$<>.]+)\(', out)}  # constructors print fully qualified
        simple = cls.rsplit('.', 1)[-1].split('$')[-1]
        if simple in names: names.add('<init>')
        members_cache[cls] = names
    return members_cache[cls]


def strings(value):
    return re.findall(r'"([^"]+)"', value) if '"' in value else []


problems, checked = [], 0
for addon in ADDONS:
    for source in sorted((HERE / addon).glob('*.java')):
        text = source.read_text(encoding='utf-8')
        mixin = re.search(r'@Mixin\s*\((.*?)\)\s*(?:public\s+)?(?:abstract\s+)?(?:class|interface)', text, re.S)
        if not mixin: continue
        imports = dict((m.group(2), m.group(1) + '.' + m.group(2)) for m in re.finditer(r'import\s+([\w.]+)\.(\w+);', text))
        package = re.search(r'package\s+([\w.]+);', text).group(1)
        spec = mixin.group(1)
        targets = strings(spec[spec.index('targets'):]) if 'targets' in spec else []
        for ref in re.findall(r'([\w.$]+)\.class', spec):
            first, _, rest = ref.partition('.')
            full = imports.get(first, ref if '.' in ref else package + '.' + ref)
            if rest and first in imports: full = imports[first] + '$' + rest.replace('.', '$')
            targets.append(full)
        methods = set()
        for m in re.finditer(r'@(?:Inject|Redirect|ModifyVariable|ModifyArg|ModifyArgs|ModifyConstant|ModifyExpressionValue|ModifyReturnValue|WrapOperation|WrapWithCondition|WrapMethod|Overwrite)\s*\((.*?)\)\s*(?:private|public|protected|static|@)', text, re.S):
            spec_m = re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', m.group(1))
            if spec_m: methods |= {s.split('(')[0] for s in strings(spec_m.group(1))}
        for target in targets:
            target = target.replace('/', '.')
            checked += 1
            if target in own: continue
            if target not in index:
                problems.append(f'{addon}/{source.name}: target class {target} not found in the shipped jars'); continue
            missing = sorted(x for x in methods if x not in members(target))
            # A mixin with several targets only needs each method in one of them.
            if missing and len(targets) > 1:
                missing = [x for x in missing if not any(t in index and x in members(t) for t in targets)]
            for name in missing: problems.append(f'{addon}/{source.name}: {target} has no method {name}')
# Every class a mixin config lists must exist under the config's package (a wrong package fails silently in game).
for addon in ADDONS:
    for config in (HERE / addon).glob('*.mixins.json'):
        data = json.loads(config.read_text(encoding='utf-8'))
        for side in ('mixins', 'client', 'server'):
            for name in data.get(side, []):
                full = data['package'] + '.' + name
                if full not in own: problems.append(f'{addon}/{config.name}: {side} lists {name}, but no class {full} exists (package?)')
                checked += 1
print(f'Checked {checked} mixin targets and config entries in {len(ADDONS)} add-ons against {len(index)} classes.')
if problems:
    print('\n'.join('MISSING ' + p for p in problems)); sys.exit(1)
print('All mixin target classes and injected methods exist.')
