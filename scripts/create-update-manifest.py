"""Create local-route-update.json from the actual signed APK; upload alongside it.

python scripts/create-update-manifest.py --apk path.apk --build-tools SDK/build-tools/36.0.0 --tag android-v0.24.16 --notes notes.txt
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=pathlib.Path, required=True)
    parser.add_argument('--build-tools', type=pathlib.Path, required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--notes', type=pathlib.Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._-]+', args.tag):
        parser.error('Invalid release tag')
    if not re.fullmatch(r'[A-Za-z0-9._-]+\.apk', args.apk.name):
        parser.error('APK filename must contain only letters, digits, dot, dash and underscore')
    badging = subprocess.check_output([str(args.build_tools / 'aapt.exe'), 'dump', 'badging', str(args.apk)], text=True, encoding='utf-8')
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    sdk = re.search(r"sdkVersion:'([0-9]+)'", badging)
    signature = subprocess.check_output([str(args.build_tools / 'apksigner.bat'), 'verify', '--print-certs', str(args.apk)], text=True, encoding='utf-8')
    certs = re.findall(r'Signer #[0-9]+ certificate SHA-256 digest: ([a-fA-F0-9]{64})', signature)
    if not package or not sdk or len(certs) != 1:
        raise SystemExit('Cannot identify one signing certificate, package or minSdk')
    if args.tag != 'android-v' + package[3]:
        raise SystemExit('Release tag must match the APK version: android-v' + package[3])
    with args.apk.open('rb') as apk_file:
        sha256 = hashlib.file_digest(apk_file, 'sha256').hexdigest()
    manifest = dict(schemaVersion=1, packageName=package[1], versionCode=int(package[2]), versionName=package[3],
                    minSdk=int(sdk[1]), certificateSha256=certs[0].lower(), sha256=sha256,
                    apkAsset=args.apk.name, size=args.apk.stat().st_size,
                    releaseUrl='https://github.com/iKokTot/local-route-android/releases/tag/' + args.tag,
                    notes=args.notes.read_text(encoding='utf-8')[:2000])
    destination = args.apk.parent / 'local-route-update.json'
    destination.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(destination)


if __name__ == '__main__':
    main()
