#!/usr/bin/env python3
from pathlib import Path
import hashlib, subprocess, sys, zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT/'VERSION').read_text(encoding='utf-8').strip()
OUT = ROOT.parent / f'Aleyon-Bridge-{VERSION}-source.zip'

EXCLUDED_DIRS = {'.git','.test-out','.full-java-stub-test','.build-cache','build'}
EXCLUDED_NAMES = {'local.properties'}

def controlled_files(include_manifest=False):
    rows=[]
    for p in ROOT.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(ROOT)
        if any(part in EXCLUDED_DIRS for part in rel.parts): continue
        if p.name in EXCLUDED_NAMES: continue
        if p.suffix.lower()=='.apk': continue
        if p.name.startswith('Aleyon-Bridge-') and p.suffix.lower()=='.zip': continue
        if not include_manifest and rel.as_posix()=='MANIFEST_SHA256.txt': continue
        rows.append((rel.as_posix(),p))
    return sorted(rows)

# Clean transient outputs before generating a source package.
for rel in ['.test-out','.full-java-stub-test','.build-cache','app/build']:
    p=ROOT/rel
    if p.exists():
        import shutil; shutil.rmtree(p)
for rel in ['local.properties']:
    p=ROOT/rel
    if p.exists(): p.unlink()

# Regenerate manifest from the complete controlled tree, not from a hand-maintained list.
manifest_lines=[]
for rel,p in controlled_files(False):
    manifest_lines.append(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {rel}')
(ROOT/'MANIFEST_SHA256.txt').write_text('\n'.join(manifest_lines)+'\n',encoding='utf-8')

# Preflight + QA must pass before packaging.
commands=[
    [sys.executable,str(ROOT/'tests/verify_package.py'),'--source-package'],
    ['bash',str(ROOT/'tests/run_core_tests.sh')],
]
for cmd in commands:
    r=subprocess.run(cmd,cwd=ROOT)
    if r.returncode: raise SystemExit(r.returncode)

# QA recreates .test-out; remove it and confirm package preflight once more.
import shutil
shutil.rmtree(ROOT/'.test-out',ignore_errors=True)
shutil.rmtree(ROOT/'.full-java-stub-test',ignore_errors=True)
r=subprocess.run([sys.executable,str(ROOT/'tests/verify_package.py'),'--source-package'],cwd=ROOT)
if r.returncode: raise SystemExit(r.returncode)

if OUT.exists(): OUT.unlink()
with zipfile.ZipFile(OUT,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for rel,p in controlled_files(True):
        z.write(p,rel)
print(OUT)
print('sha256='+hashlib.sha256(OUT.read_bytes()).hexdigest())
