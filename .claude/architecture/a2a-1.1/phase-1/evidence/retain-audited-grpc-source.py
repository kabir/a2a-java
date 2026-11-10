from pathlib import Path
import json,re,hashlib,tarfile,gzip,io,zipfile
run=Path('/tmp/a2a-phase1-run');root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');ev=root/'.claude/architecture/a2a-1.1/phase-1/evidence';extracted=run/'grpc-source-review'
reports=['resume-provider-design-review.md','resume-implementation-review.md'];names=set(re.findall(r'\b([A-Za-z_$][\w$]*\.java)\b','\n'.join((run/p).read_text() for p in reports)));names.add('NettyClientHandler.java')
archives=[]
for artifact in ('grpc-api','grpc-core','grpc-netty'):
 p=Path.home()/f'.m2/repository/io/grpc/{artifact}/1.81.0/{artifact}-1.81.0-sources.jar';archives.append((p,zipfile.ZipFile(p)))
rows=[];content=[]
for p in sorted(extracted.rglob('*.java')):
 if p.name not in names:continue
 entry=str(p.relative_to(extracted));data=p.read_bytes();matches=[a for a,z in archives if entry in z.namelist() and z.read(entry)==data];assert len(matches)==1,(entry,matches)
 rows.append({'entry':entry,'sha256':hashlib.sha256(data).hexdigest(),'source_archive':matches[0].name});content.append((entry,data))
assert rows
archive=ev/'resume-audited-grpc-source.tar.gz'
with archive.open('wb') as raw:
 with gzip.GzipFile(fileobj=raw,mode='wb',mtime=0) as gz:
  with tarfile.open(fileobj=gz,mode='w') as tar:
   for name,data in content:
    info=tarfile.TarInfo(name);info.size=len(data);info.mtime=0;tar.addfile(info,io.BytesIO(data))
with tarfile.open(archive) as tar:
 for row in rows:assert hashlib.sha256(tar.extractfile(row['entry']).read()).hexdigest()==row['sha256']
manifest={'purpose':'Exact pinned upstream source entries referenced by independent provider reviews; original /tmp source links can be resolved by extracting these entries. Selected source audit, not whole-library proof.','grpc_version':'1.81.0','archive':archive.name,'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'original_extract_root':str(extracted),'source_archives':[{'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p,z in archives],'files':rows};(ev/'resume-audited-grpc-source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(len(rows),'exact source entries archived and matched upstream source jars')
