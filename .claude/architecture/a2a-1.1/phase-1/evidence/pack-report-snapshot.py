from pathlib import Path
import tarfile,gzip,io,hashlib,json,shutil,sys
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');ev=root/'.claude/architecture/a2a-1.1/phase-1/evidence';name=sys.argv[1];folder=ev/name;assert folder.is_dir() and folder.parent==ev
archive=ev/(name+'.tar.gz');rows=[]
with archive.open('wb') as raw:
 with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as gz:
  with tarfile.open(fileobj=gz,mode='w') as tar:
   for p in sorted(folder.rglob('*.xml.gz')):
    data=gzip.decompress(p.read_bytes());relative=str(p.relative_to(folder).with_suffix(''));entry=tarfile.TarInfo(relative);entry.size=len(data);entry.mtime=0;tar.addfile(entry,io.BytesIO(data));rows.append({'path':relative,'sha256':hashlib.sha256(data).hexdigest()})
with tarfile.open(archive) as tar:
 assert len(tar.getmembers())==len(rows)
 for row in rows:assert hashlib.sha256(tar.extractfile(row['path']).read()).hexdigest()==row['sha256']
manifest={'archive':archive.name,'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'report_count':len(rows),'reports':rows};(ev/(name+'-manifest.json')).write_text(json.dumps(manifest,indent=2)+'\n')
shutil.rmtree(folder)
print(archive.name,len(rows),'exact XML reports,',archive.stat().st_size,'bytes')
