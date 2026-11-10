from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,shutil,tarfile
fixture=Path('/tmp/a2a-phase0-deliverable/fixtures')
evidence=Path('/tmp/a2a-phase0-deliverable/evidence')
repo=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java')
out=Path('/tmp/a2a-phase0-frozen-runtime')
assert not out.exists(), 'Refuse to replace a frozen runtime'
out.mkdir()
entries={};versions={}
for version in ['1.0','0.3']:
    source_cp=(fixture/('classpath-'+version+'.original.txt')).read_text().strip().split(':')
    copied=[]
    for source in source_cp:
        p=Path(source)
        assert p.exists(), source
        if source not in entries:
            sdk=p.is_relative_to(repo)
            relative=Path('sdk' if sdk else 'dependencies')/(str(len(entries)).zfill(3)+'-'+p.name)
            dest=out/relative
            dest.parent.mkdir(exist_ok=True)
            if p.is_dir(): shutil.copytree(p,dest)
            else: shutil.copy2(p,dest)
            files=sorted(x for x in dest.rglob('*') if x.is_file()) if dest.is_dir() else [dest]
            hashes={str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in files}
            entries[source]={'source':source,'snapshot':str(relative),'sdk':sdk,'sha256':hashes}
        copied.append(str(out/entries[source]['snapshot']))
    (out/('classpath-'+version+'.txt')).write_text(':'.join(copied)+'\n')
    versions[version]=source_cp
manifest={'source_revision':'2817ed78d02d9a6e609dafdf7f5129484edef017','snapshot_utc':datetime.now(timezone.utc).isoformat(),'source':'unchanged successful Java17 baseline; no SDK rebuild','runtime_root':str(out),'versions':versions,'entries':list(entries.values()),'archive_scope':'SDK class directories and jars plus manifest; third-party dependencies are SHA256-pinned, copied to runtime for this check, not bundled in retained SDK archive'}
(out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
shutil.copy(out/'manifest.json',evidence/'historical-runtime-manifest.json')
with tarfile.open(evidence/'historical-sdk-runtime.tar.gz','w:gz') as archive:
    archive.add(out/'sdk',arcname='sdk')
    archive.add(out/'manifest.json',arcname='manifest.json')
print(json.dumps({'entries':len(entries),'sdk_entries':sum(e['sdk'] for e in entries.values()),'files':sum(len(e['sha256']) for e in entries.values()),'archive_bytes':(evidence/'historical-sdk-runtime.tar.gz').stat().st_size}))
