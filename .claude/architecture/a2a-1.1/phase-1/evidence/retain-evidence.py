from pathlib import Path
import gzip,json,re,shutil,hashlib,datetime,sys,xml.etree.ElementTree as E
run=Path('/tmp/a2a-phase1-run')
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java')
ev=root/'.claude/architecture/a2a-1.1/phase-1/evidence'
ev.mkdir(exist_ok=True)
commands=[]
for meta in sorted(run.glob('*.json')):
    try:d=json.loads(meta.read_text())
    except (ValueError,OSError):continue
    if not isinstance(d,dict) or not {'command','start','deadline_seconds'} <= d.keys():continue
    suites=[];log=meta.with_suffix('.log')
    if log.exists():
        data=log.read_bytes();(ev/(log.name+'.gz')).write_bytes(gzip.compress(data,mtime=0))
        for line in data.decode(errors='replace').splitlines():
            m=re.search(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in (.*)',line)
            if m:suites.append(dict(zip(('discovered','failures','errors','skipped','suite'),[int(x) for x in m.groups()[:4]]+[m.group(5)])))
    shutil.copyfile(meta,ev/meta.name)
    d['label']=meta.stem;d['actual_log_suite_totals']={k:sum(s[k] for s in suites) for k in ('discovered','failures','errors','skipped')};d['actual_log_suites']=suites;commands.append(d)
(run/'command-summary.json').write_text(json.dumps(commands,indent=2)+'\n')
shutil.copyfile(run/'command-summary.json',ev/'command-summary.json')
for name in ['bounded.py','freeze-old-client.py','retain-evidence.py','source-compatibility.json','recompiled-provider-compile.log','legacy-provider-compile.log','legacy-classpath.txt','candidate-classpath.txt','client-review.md','server-review.md','fresh-review.md','attempt-audit.md','environment.json','native03-focused-summary.json']:
    shutil.copyfile(run/name,ev/name)
label=sys.argv[1] if len(sys.argv)>1 else 'full-changed-reactor'
meta=json.loads((run/(label+'.json')).read_text())
assert 'end' in meta,'Do not summarize a still-running full build'
start=datetime.datetime.fromisoformat(meta['start']).timestamp()
rows=[]
for p in sorted(root.rglob('target/*-reports/TEST-*.xml')):
    if p.stat().st_mtime < start or p.stat().st_mtime > datetime.datetime.fromisoformat(meta['end']).timestamp()+2:continue
    node=E.parse(p).getroot()
    skipped=[{'class':c.get('classname'),'name':c.get('name'),'reason':c.find('skipped').get('message') or c.find('skipped').text or ''} for c in node.findall('testcase') if c.find('skipped') is not None]
    rows.append({'report':str(p.relative_to(root)),'kind':p.parent.name,'suite':node.get('name'),'discovered':int(node.get('tests','0')),'failures':int(node.get('failures','0')),'errors':int(node.get('errors','0')),'skipped':int(node.get('skipped','0')),'skipped_cases':skipped,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
summary={'command':meta,'report_count':len(rows),'kinds':{k:sum(r['kind']==k for r in rows) for k in sorted({r['kind'] for r in rows})},'totals':{k:sum(r[k] for r in rows) for k in ('discovered','failures','errors','skipped')},'reports':rows}
summary['totals']['executed']=summary['totals']['discovered']-summary['totals']['skipped']
(ev/(label+'-test-summary.json')).write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({'commands':len(commands),'reports':summary['report_count'],'kinds':summary['kinds'],'totals':summary['totals']},indent=2))
