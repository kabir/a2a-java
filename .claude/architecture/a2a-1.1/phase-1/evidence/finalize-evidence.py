from pathlib import Path
import subprocess,json,gzip,hashlib,shutil,collections,datetime
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');base=root/'.claude/architecture/a2a-1.1';ev=base/'phase-1/evidence';run=Path('/tmp/a2a-phase1-run')
old=json.loads((base/'phase-0/evidence/baseline-test-summary.json').read_text());new=json.loads((ev/'full-changed-reactor-final-test-summary.json').read_text())
before=collections.Counter((x['class'],x['test']) for x in old['skips'])
after=collections.Counter((x['class'],x['name']) for r in new['reports'] for x in r['skipped_cases'])
assert before==after,(list((before-after).elements()),list((after-before).elements()))
comparison={'compared_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'historical_baseline':'../../phase-0/evidence/baseline-test-summary.json','candidate_summary':'full-changed-reactor-final-test-summary.json','baseline_skipped':sum(before.values()),'candidate_skipped':sum(after.values()),'same_skipped_test_identities':True,'newly_skipped':[],'formerly_skipped_now_executed':[]}
(ev/'skip-identity-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n')
first=run/'first-full-build-failure';dest=ev/'first-full-build-failure';dest.mkdir(exist_ok=True)
for p in sorted(first.rglob('*')):
 if p.is_file():
  q=dest/p.relative_to(first);q.parent.mkdir(parents=True,exist_ok=True);q.with_name(q.name+'.gz').write_bytes(gzip.compress(p.read_bytes(),mtime=0))
def git(*args):return subprocess.check_output(['git','-c','core.fsmonitor=false',*args],cwd=root)
tracked=git('diff','--name-only','HEAD').decode().splitlines();untracked=git('ls-files','--others','--exclude-standard').decode().splitlines()
source=[p for p in tracked+untracked if not p.startswith('.claude/')]
assert len(source)==len(set(source))
patch=git('diff','--binary','HEAD','--',*[p for p in tracked if p in source])
for p in untracked:
 if p not in source:continue
  # Separate no-index additions keep the index untouched, including binary fixtures.
 result=subprocess.run(['git','-c','core.fsmonitor=false','diff','--no-index','--binary','--','/dev/null',p],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 assert result.returncode==1,(p,result.returncode,result.stderr.decode());patch+=result.stdout
(ev/'source-and-tests.diff.gz').write_bytes(gzip.compress(patch,mtime=0))
rows=[]
for p in sorted(source):
 data=(root/p).read_bytes();rows.append({'path':p,'state':'modified' if p in tracked else 'new','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
summary={'base_commit':git('rev-parse','HEAD').decode().strip(),'index_unchanged':True,'source_and_test_file_count':len(rows),'modified':sum(x['state']=='modified' for x in rows),'new':sum(x['state']=='new' for x in rows),'source_patch':'source-and-tests.diff.gz','source_patch_uncompressed_sha256':hashlib.sha256(patch).hexdigest(),'source_patch_uncompressed_bytes':len(patch),'files':rows}
(ev/'source-scope.json').write_text(json.dumps(summary,indent=2)+'\n')
for name in ['verify-final-integrity.py','finalize-evidence.py']:shutil.copyfile(run/name,ev/name)
print(json.dumps({k:v for k,v in summary.items() if k!='files'},indent=2));print(json.dumps(comparison,indent=2));print('Retained first-failure files:',sum(p.is_file() for p in dest.rglob('*')))
