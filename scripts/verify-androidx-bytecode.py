#!/usr/bin/env python3
"""Check untransformed library AAR/JARs for legacy Support Library references."""

import argparse
import io
from pathlib import Path
import sys
import zipfile


def scan(data, label):
    failures = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith('/'):
                continue
            content = archive.read(name)
            location = f'{label}!/{name}'
            if name.endswith(('.jar', '.aar')):
                failures.extend(scan(content, location))
            elif any(marker in content for marker in (
                b'android/support/', b'android.support.', b'com.android.support',
                'android.support.'.encode('utf-16le'),
            )):
                failures.append(location)
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', nargs='+', type=Path)
    args = parser.parse_args()
    failures = []
    for path in args.artifacts:
        found = scan(path.read_bytes(), str(path))
        failures.extend(found)
        print(f'{"FAIL" if found else "PASS"}: {path}')
    if failures:
        print('\nLegacy references:\n' + '\n'.join(failures), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
