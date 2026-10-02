"""Run before publishing source or pack archives. Prints filenames, never secret values."""
from pathlib import Path
import io, re, subprocess, sys, zipfile

PATTERNS = {
    'private key': rb'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',
    'access token': rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16}|xox[baprs]-[A-Za-z0-9-]{20,})',
    'personal computer path': rb'[A-Za-z]:[\\/]+Users[\\/]+(?!Public\b|<|YOUR_|username\b|example\b)[^\s"\r\n]+',
    'private support link': rb'www\.microsoft\.com/en-us/wdsi/submission/[0-9a-f-]{36}',
    'hardcoded SSH destination': rb'[A-Za-z_][A-Za-z0-9_-]*@(?:\d{1,3}\.){3}\d{1,3}',
    'saved account credential': rb'(?i)"(?:accessToken|refreshToken|password)"\s*:\s*"[^"\s]{8,}',
}
PRIVATE_NAMES = {'accounts.json', 'launcher_accounts.json', 'launcher_profiles.json', 'usercache.json', 'ops.json', 'whitelist.json', 'mpsupportfiles.cab'}
failures = []
def scan(data, name, depth=0):
    base = name.split('!')[-1].replace('\\', '/').rsplit('/', 1)[-1].lower()
    if base in PRIVATE_NAMES or base.endswith('.cab'):
        failures.append((name, 'private account or diagnostic file'))
    for kind, pattern in PATTERNS.items():
        if re.search(pattern, data): failures.append((name, kind))
    if data.startswith(b'PK\x03\x04'):
        if depth >= 5: raise ValueError('Too many nested archives: '+name)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if sum(info.file_size for info in archive.infolist()) > 300_000_000:
                raise ValueError('Archive too large for bounded audit: '+name)
            for info in archive.infolist():
                if not info.is_dir(): scan(archive.read(info), name+'!'+info.filename, depth+1)
def main():
    root = Path(__file__).resolve().parents[1]
    if len(sys.argv) > 1:
        files = [Path(p) for p in sys.argv[1:]]
    else:
        names = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z']).decode().split('\0')
        files = [root/name for name in names if name]
    for path in files:
        if path.is_file(): scan(path.read_bytes(), path.name if len(sys.argv)>1 else path.relative_to(root).as_posix())
    for name, kind in sorted(set(failures)): print(kind+': '+name)
    if failures: return 1
    print('Public-data check passed for '+str(len(files))+' files and their nested archives.')
    return 0
if __name__ == '__main__': sys.exit(main())
