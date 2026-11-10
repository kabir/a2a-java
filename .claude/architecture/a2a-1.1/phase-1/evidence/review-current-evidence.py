from pathlib import Path
import datetime as D, hashlib, json, re, sys, xml.etree.ElementTree as E
ROOT = Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java')
RUN = Path('/tmp/a2a-phase1-run')
label = sys.argv[1] if len(sys.argv) > 1 else 'resume-full-current-reactor'
metadata = json.loads((RUN / (label + '.json')).read_text())
if 'end' not in metadata:
    raise SystemExit('Current full command has not completed; no full result asserted.')
start = D.datetime.fromisoformat(metadata['start']).timestamp()
end = D.datetime.fromisoformat(metadata['end']).timestamp()
reports = []
skips = set()
for path in sorted(ROOT.rglob('TEST-*.xml')):
    if path.parent.name not in ('surefire-reports', 'failsafe-reports') or not start <= path.stat().st_mtime <= end + 2:
        continue
    suite = E.parse(path).getroot()
    counts = {key: int(suite.attrib.get(key, '0')) for key in ('tests', 'failures', 'errors', 'skipped')}
    relative = str(path.relative_to(ROOT))
    module = relative.split('/target/')[0]
    for case in suite.findall('testcase'):
        if case.find('skipped') is not None:
            skips.add((module, case.attrib.get('classname', suite.attrib.get('name', '')), case.attrib['name']))
    reports.append({'path': relative, **counts, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
baseline = json.loads((ROOT / '.claude/architecture/a2a-1.1/phase-0/evidence/baseline-test-summary.json').read_text())
old = {(s['module'], s['class'], s['test']) for s in baseline['skips']}
log = (RUN / (label + '.log')).read_text()
reactor_summary = log[log.rfind('Reactor Summary'):] if 'Reactor Summary' in log else log
summary = {'reviewer': 'correction_review', 'metadata': metadata,
           'report_count': len(reports), 'counts': {k: sum(r[k] for r in reports) for k in ('tests', 'failures', 'errors', 'skipped')},
           'successful_modules': len(re.findall(r'^\[INFO\] .*?\bSUCCESS[ \t]+\[', reactor_summary, re.M)),
           'failed_modules': len(re.findall(r'^\[INFO\] .*?\bFAILURE[ \t]+\[', reactor_summary, re.M)),
           'skip_identities': sorted(skips), 'new_skip_identities': sorted(skips-old),
           'missing_baseline_skip_identities': sorted(old-skips), 'reports': reports}
summary['counts']['executed'] = summary['counts']['tests'] - summary['counts']['skipped']
out = RUN / ('resume-final-independent-full-summary.json' if label == 'resume-full-current-reactor' else label + '-independent-review-summary.json')
out.write_text(json.dumps(summary, indent=2)+'\n')
print(json.dumps({k:v for k,v in summary.items() if k not in ('reports','skip_identities','metadata')}, indent=2))
print(out)
