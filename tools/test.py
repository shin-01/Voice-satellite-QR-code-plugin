#!/usr/bin/env python3
"""Exercise the QR Code plugin against the SDK without a device."""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from android_sdk import android_platform

root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')


def tool(name):
    return str(Path(java_home) / 'bin' / name) if java_home else name


sdk_root = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'android-sdk'))))
try:
    platform = str(android_platform(sdk_root))
except ValueError as error:
    raise SystemExit(str(error)) from error

sources = [*sorted((root / 'sdk/src').rglob('*.java')),
           *sorted((root / 'src').rglob('*.java')),
           *sorted((root / 'tests').rglob('*.java'))]

with tempfile.TemporaryDirectory(prefix='kiosk-plugin-test-') as directory:
    subprocess.run([tool('javac'), '--release', '8', '-cp', platform, '-d', directory, *map(str, sources)], check=True)
    subprocess.run([tool('java'), '-ea', '-cp', os.pathsep.join([directory, platform]), 'example.kiosk.QrPluginTest'], check=True)

sys.path.insert(0, str(root / 'tools'))
from plugin_manifest import build_manifest
manifest_bytes = build_manifest(root / 'kiosk-satellite-plugin.json', None)
print('Manifest OK,', len(manifest_bytes), 'bytes')
print('QrPlugin tests OK')
