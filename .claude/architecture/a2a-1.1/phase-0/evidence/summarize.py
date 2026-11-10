import datetime,json,xml.etree.ElementTree as ET
from pathlib import Path
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java')
meta=json.loads(Path('/tmp/a2a-phase0-run/baseline-full.json').read_text())
start=datetime.datetime.fromisoformat(meta['start']).timestamp()
suites=[];skips=[];problems=[]
for pattern in ['**/target/surefire-reports/TEST-*.xml','**/target/failsafe-reports/TEST-*.xml']:
 for p in sorted(root.glob(pattern)):
  if p.stat().st_mtime < start: continue
  node=ET.parse(p).getroot(); module=str(p.relative_to(root)).split('/target/')[0]
  entry={'module':module,'report':str(p.relative_to(root)),'suite':node.get('name'),'tests':int(node.get('tests','0')),'failures':int(node.get('failures','0')),'errors':int(node.get('errors','0')),'skipped':int(node.get('skipped','0'))}
  suites.append(entry)
  for case in node.findall('.//testcase'):
   for tag,target in [('skipped',skips),('failure',problems),('error',problems)]:
    for elem in case.findall(tag):
     target.append({'module':module,'class':case.get('classname'),'test':case.get('name'),'type':tag,'message':elem.get('message',''),'reason':(elem.text or '').strip()[:1500]})
result={'build':meta,'totals':{k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']},'suite_reports':len(suites),'suites':suites,'skips':skips,'problems':problems}
Path('/tmp/a2a-phase0-deliverable/evidence/baseline-test-summary.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'build':meta,'totals':result['totals'],'suite_reports':len(suites),'problem_count':len(problems)},indent=2))
