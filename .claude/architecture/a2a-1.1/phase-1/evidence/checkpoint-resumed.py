from pathlib import Path
import subprocess,json,gzip,datetime as D,shutil,hashlib,collections
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');base=root/'.claude/architecture/a2a-1.1';ev=base/'phase-1/evidence';run=Path('/tmp/a2a-phase1-run')
completed=int(__import__('sys').argv[1]);assert completed in (4,5)
commands=json.loads((ev/'command-summary.json').read_text());audit=json.loads((ev/'pre-resume/run-control-audit.json').read_text());intervals=[];groups=collections.defaultdict(list);skews=[]
for c in commands:
 start=D.datetime.fromisoformat(c['start']);end=D.datetime.fromisoformat(c['end'])
 if start>=D.datetime.fromisoformat('2026-11-10T18:37:06+00:00'):group='resumed';deadline=D.datetime.fromisoformat('2026-11-10T20:37:06+00:00')
 elif start>=D.datetime.fromisoformat('2026-11-10T17:33:12+00:00'):group='correction';deadline=D.datetime.fromisoformat('2026-11-10T18:33:12+00:00')
 else:group='original';deadline=D.datetime.fromisoformat('2026-11-10T16:57:25+00:00')
 assert end<=deadline,(c['label'],end,deadline)
 assert c['deadline_seconds']<=min(1200,(deadline-start).total_seconds())+1,(c['label'],c['deadline_seconds'])
 assert not c.get('timed_out',False),c['label']
 assert c['elapsed_seconds']<=c['deadline_seconds']+30,c['label']
 span=round((end-start).total_seconds(),3)
 if abs(span-c['elapsed_seconds'])>1:skews.append({'label':c['label'],'utc_span_seconds':span,'monotonic_elapsed_seconds':c['elapsed_seconds'],'both_recorded_unchanged':True})
 intervals.append((start,end,c['label']));groups[group].append(c['label'])
intervals.sort();overlap=[(a[2],b[2]) for a,b in zip(intervals,intervals[1:]) if a[1]>b[0]];assert not overlap,overlap
stamp=D.datetime.now(D.timezone.utc).isoformat();auth=json.loads((run/'resume-authorization.json').read_text());auth.update(completed_iterations=completed,active_iteration=5 if completed==4 else None,budget_exhausted=completed==5,cumulative_phase1_completed_iterations=12+completed)
if completed==5:auth.update(completed_at=stamp,exhaustion_reason='five additional iterations completed with verified Phase 1 handoff; no implementation continuation remains')
(run/'resume-authorization.json').write_text(json.dumps(auth,indent=2)+'\n');shutil.copyfile(run/'resume-authorization.json',ev/'resume-authorization.json')
audit.update(checked_utc=stamp,bounded_commands=len(commands),original_command_count=len(groups['original']),correction_command_count=len(groups['correction']),resumed_command_count=len(groups['resumed']),overlapping_intervals=overlap,all_command_bounds_within_authorized_run=True,timeouts=0,utc_and_monotonic_differences=skews,resumed_authorization=auth,F1_status='corrected within approved pinned owned-channel profile; independent final review retained',historical_F1_failed_material_cycles=3,resumed_F1_failed_material_cycles=0,resumed_verified_revised_implementation_cycles=1,whitespace_fixture_isolation_fix_cycles=1,commit_ready=completed==5,commit_authorized=False,source_and_fixtures_frozen=True)
(ev/'run-control-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
for name in ['resume-provider-design-review.md','resume-implementation-review.md','resume-cleanup-report.md','resume-migration-report.md','resume-lifecycle-report.md','resume-final-independent-review.md','resume-whitespace-independent-source-review.json','resume-final-build-inputs.json','pack-report-snapshot.py','refresh-current-documents.py','checkpoint-resumed.py','review-current-evidence.py','compile-itk-expression.py']:
 shutil.copyfile(run/name,ev/name)
for p in (run/'itk-type-probe').iterdir():
 if p.is_file():
  (ev/'itk-type-probe').mkdir(exist_ok=True);shutil.copyfile(p,ev/'itk-type-probe'/p.name)
patch=gzip.decompress((ev/'source-and-tests.diff.gz').read_bytes());stat=subprocess.run(['git','apply','--stat'],input=patch,cwd=root,stdout=subprocess.PIPE,check=True).stdout;(ev/'source-diffstat.txt').write_bytes(stat)
def git(*args):return subprocess.check_output(['git','-c','core.fsmonitor=false',*args],cwd=root,text=True).splitlines()
files=sorted(set(git('diff','--name-only','HEAD')+git('ls-files','--others','--exclude-standard')));(ev/'commit-files.txt').write_text('\n'.join(files)+'\n')
scope=json.loads((ev/'source-scope.json').read_text());frozen=json.loads((run/'resume-final-build-inputs.json').read_text());assert {r['path']:r['sha256'] for r in scope['files']}=={r['path']:r['sha256'] for r in frozen['files']}
assert all(hashlib.sha256((root/r['path']).read_bytes()).hexdigest()==r['sha256'] for r in scope['files'])
comparison={'checked_utc':stamp,'frozen_build_inputs':len(frozen['files']),'functional_hashes_match':True,'complete_proposed_paths':len(files),'functional_input_manifest':'resume-final-build-inputs.json','source_scope':'source-scope.json','inventory':'commit-files.txt','source_diffstat':stat.decode().splitlines()[-1]};(ev/'resume-candidate-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n')
# Include newly created administrative comparison in inventory.
files=sorted(set(git('diff','--name-only','HEAD')+git('ls-files','--others','--exclude-standard')));(ev/'commit-files.txt').write_text('\n'.join(files)+'\n');comparison['complete_proposed_paths']=len(files);(ev/'resume-candidate-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n')
print(json.dumps({'bounded_commands':len(commands),'groups':{k:len(v) for k,v in groups.items()},'iterations_completed':completed,'candidate':comparison},indent=2))
