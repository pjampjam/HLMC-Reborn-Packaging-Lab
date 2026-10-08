"""Boot the local test server (gradlew :dev:runServer), wait for "Done", report problems, then stop it cleanly.

Usage: python boot-test-server.py [SECONDS]   (default 600). Needs eula=true in run/server/eula.txt (the owner's choice).
Prints the mod count, Holy Lois lines and every ERROR / mixin failure from the log; exit 1 when the server never got ready.
"""
import os, re, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOG = HERE / 'run/server/logs/latest.log'
limit = int(sys.argv[1]) if len(sys.argv) > 1 else 600
gradle = str(HERE / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
if LOG.exists(): LOG.unlink()
server = subprocess.Popen([gradle, ':dev:runServer', '--console=plain', '-q'], cwd=HERE, stdin=subprocess.PIPE,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, text=True)
start, ready = time.time(), False
while time.time() - start < limit and server.poll() is None:
    time.sleep(5)
    text = LOG.read_text(encoding='utf-8', errors='replace') if LOG.exists() else ''
    if re.search(r'\]: Done \(', text): ready = True; break
    if re.search(r'Incompatible mods found|Failed to load datapacks|Crash report saved|Exception in server tick loop', text): break
if server.poll() is None:
    if ready: server.stdin.write('stop\n'); server.stdin.flush()
    try: server.wait(timeout=120 if ready else 5)
    except subprocess.TimeoutExpired: server.kill()
# A failed boot can leave the server JVM (a Gradle daemon child) hanging: stop exactly that dev server process.
if os.name == 'nt':
    subprocess.run(['powershell', '-NoProfile', '-Command',
                    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*devlaunchinjector*' "
                    "-and $_.CommandLine -like '*KnotServer*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"], capture_output=True)
else:
    subprocess.run(['pkill', '-f', 'devlaunchinjector.*KnotServer'], capture_output=True)
text = LOG.read_text(encoding='utf-8', errors='replace') if LOG.exists() else ''
mods = re.search(r'Loading (\d+) mods', text)
print('ready:', ready, f'({int(time.time() - start)} s)', '| mods:', mods.group(1) if mods else '?')
for line in text.splitlines():
    if re.search(r'holylois|Holy Lois', line, re.I) and 'INFO' in line: print('  ' + line[:200])
problems = [l for l in text.splitlines() if re.search(r'/ERROR\]|Mixin apply failed|InvalidInjectionException|could not find any targets|Critical injection failure', l)]
print(f'{len(problems)} error lines'); print('\n'.join('  ' + l[:240] for l in problems[:25]))
sys.exit(0 if ready else 1)
