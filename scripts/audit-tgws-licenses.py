"""Generate bundled Rust notices from the pinned lockfile and verified crates.io sources.

Cargo.lock includes a conservative superset (host/dev/platform dependencies).
No crate code is executed. Published crate checksums are verified before reading.
"""
import concurrent.futures
import hashlib
import io
import pathlib
import re
import tarfile
import tomllib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/rust/tg-ws-proxy-rs'
OUTPUT = ROOT / 'app/src/main/assets/licenses/tgws'
CACHE = ROOT / 'work/tgws-crates'
OUTPUT.mkdir(parents=True, exist_ok=True)
CACHE.mkdir(parents=True, exist_ok=True)
packages = [p for p in tomllib.loads((SOURCE / 'Cargo.lock').read_text())['package'] if p.get('source', '').startswith('registry+')]


def audit(package):
    name, version = package['name'], package['version']
    identity = f'{name}-{version}'
    cache = CACHE / f'{identity}.crate'
    if not cache.exists():
        with urllib.request.urlopen(f'https://static.crates.io/crates/{name}/{identity}.crate', timeout=60) as response:
            cache.write_bytes(response.read())
    archive = cache.read_bytes()
    assert hashlib.sha256(archive).hexdigest() == package['checksum'], identity
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as tar:
        metadata = tomllib.loads(tar.extractfile(f'{identity}/Cargo.toml').read().decode())['package']
        texts = []
        for member in tar.getmembers():
            if member.isfile() and re.search(r'(^|/)(LICENSE|LICENCE|COPYING|NOTICE|UNLICENSE)([._/-]|$)', member.name, re.I):
                texts.append(f'--- {member.name} ---\n' + tar.extractfile(member).read().decode('utf-8', errors='replace'))
        license_name = metadata.get('license', metadata.get('license-file', 'UNDECLARED'))
        report = f'{identity}\nSPDX: {license_name}\nSource: https://crates.io/crates/{name}/{version}\nSHA256: {package["checksum"]}\n\n'
        report += '\n\n'.join(texts) if texts else 'No standalone license file in crate archive; see declared SPDX license and upstream source.\n'
        (OUTPUT / f'{identity}.txt').write_text(report, encoding='utf-8')
        return identity, license_name, bool(texts)


with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    results = sorted(pool.map(audit, packages))
(OUTPUT / 'DEPENDENCIES.md').write_text('# TGWs Rust dependency notices\n\nPinned tg-ws-proxy-rs 2.5.4 Cargo.lock; conservative superset including host/dev crates.\n\n' + '\n'.join(f'* {name}: {license_name}' for name, license_name, _ in results) + '\n', encoding='utf-8')
(OUTPUT / 'tg-ws-proxy-rs-MIT.txt').write_bytes((SOURCE / 'LICENSE').read_bytes())
print(f'Verified {len(results)} crate checksums and generated license notices.')
print('License expressions:', sorted(set(license_name for _, license_name, _ in results)))
print('No standalone license file:', [name for name, _, present in results if not present])
