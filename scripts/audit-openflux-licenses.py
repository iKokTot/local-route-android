"""Bundle notices for modules actually linked into the pinned Android OpenFlux helper."""
import argparse
import json
import pathlib
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--core', type=pathlib.Path, required=True)
parser.add_argument('--go', required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
core = args.core.resolve()
go = str(pathlib.Path(args.go).resolve())
subprocess.run([go, 'mod', 'verify'], cwd=core / 'mobile', check=True)
raw = subprocess.check_output([go, 'list', '-m', '-json', 'all'], cwd=core / 'mobile', text=True)
decoder = json.JSONDecoder()
modules = {}
while raw.strip():
    item, offset = decoder.raw_decode(raw.lstrip())
    raw = raw.lstrip()[offset:]
    modules[item['Path']] = item
binary = root / 'app/build/generated/openfluxJniLibs/arm64-v8a/libopenflux.so'
linked = subprocess.check_output([go, 'version', '-m', str(binary)], text=True)
linked = 'libopenflux.so:' + linked.splitlines()[0].split(':', 2)[-1] + '\n' + '\n'.join(linked.splitlines()[1:])
names = re.findall(r'^\s*dep\s+(\S+)', linked, re.M)
output = root / 'app/src/main/assets/licenses/openflux'
output.mkdir(parents=True, exist_ok=True)
missing = []
for name in names:
    module = modules[name]
    source = pathlib.Path(module.get('Replace', module)['Dir'])
    files = [file for file in source.iterdir() if file.is_file() and file.name.lower().startswith(('license', 'copying', 'notice', 'copyright'))]
    if not files:
        missing.append(name)
        continue
    text = f"Module: {name}\nVersion: {module.get('Version', '(local)')}\nSum: {module.get('Sum', '(pinned local source)')}\n\n"
    for file in sorted(files):
        text += f'--- {file.name} ---\n' + file.read_text(encoding='utf-8', errors='replace') + '\n\n'
    (output / (name.replace('/', '__') + '.txt')).write_text(text, encoding='utf-8')
if missing:
    raise SystemExit('Missing notices: ' + ', '.join(missing))
(output / 'Go-LICENSE.txt').write_bytes((pathlib.Path(go).parent.parent / 'LICENSE').read_bytes())
(output / 'DEPENDENCIES.md').write_text('# OpenFlux linked Go modules\n\n' + linked + '\n', encoding='utf-8')
print(f'Bundled notices for {len(names)} linked Go modules and the Go runtime.')
