"""Classes shipped loose by one mod jar that also exist in another (stray library copies). Usage: python check-duplicate-classes.py run/client/mods"""
import zipfile, io, glob, os, sys, json, collections
owners = collections.defaultdict(set)
def modid(z):
    try: return json.loads(z.read('fabric.mod.json'))['id']
    except Exception: return None
def scan(z, name, top):
    for n in z.namelist():
        if n.endswith('.jar'):
            try:
                inner = zipfile.ZipFile(io.BytesIO(z.read(n)))
                scan(inner, (modid(inner) or n.split('/')[-1]) + '(nested)', top)
            except Exception: pass
        elif n.endswith('.class') and not n.startswith(('META-INF/', 'module-info')):
            owners[n].add((top, name))
for j in sorted(glob.glob(sys.argv[1] + '/*.jar')):
    z = zipfile.ZipFile(j); scan(z, modid(z) or os.path.basename(j), os.path.basename(j))
# Only count it when a class is shipped loose (shaded) by a top-level jar AND exists elsewhere.
by_pkg = collections.defaultdict(set)
for cls, where in owners.items():
    tops = {t for t, _ in where}
    if len(tops) > 1 and any(not n.endswith('(nested)') for _, n in where):
        pkg = '/'.join(cls.split('/')[:4])
        by_pkg[pkg] |= {n for _, n in where}
for pkg, who in sorted(by_pkg.items()): print(pkg, '->', sorted(who))
