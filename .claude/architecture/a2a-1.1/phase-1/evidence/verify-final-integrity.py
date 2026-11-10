from pathlib import Path
import subprocess,json,hashlib,tarfile,re,datetime
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');base=root/'.claude/architecture/a2a-1.1';ev=base/'phase-1/evidence'
def git(*args):return subprocess.check_output(['git','-c','core.fsmonitor=false',*args],cwd=root,text=True).strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
expected='e468dd70a415d17c19ae70d75750b6464d983ad1'
assert git('branch','--show-current')=='support/a2a-1.1.0'
assert git('rev-parse','HEAD')==expected
subprocess.run(['git','-c','core.fsmonitor=false','diff','--cached','--exit-code'],cwd=root,check=True,capture_output=True)
preserved=['.claude/architecture/a2a-1.1/phase-0','spec-grpc']+['.claude/architecture/a2a-1.1/'+name for name in ['README.md','architecture.md','spec-delta.md','compatibility.md','validation.md','acceptance.md','implementation-loop.md']]
assert not git('status','--porcelain','--untracked-files=all','--',*preserved)
subprocess.run(['git','-c','core.fsmonitor=false','diff','--exit-code',expected,'--',*preserved],cwd=root,check=True,capture_output=True)
manifest=json.loads((base/'phase-0/fixtures/manifest.json').read_text())
for entry in manifest['files']:assert digest(base/'phase-0/fixtures'/entry['file'])==entry['sha256'],entry['file']
prov=json.loads((base/'phase-1/compatibility/fixture-provenance.json').read_text())
for name,sha in prov['files'].items():assert digest(root/name)==sha,name
frozen=base/'phase-0/fixtures/expected/1.0/rpc-GET_TASK_TEST_RESPONSE.json';copy=root/'client/base/src/test/resources/legacy-providers/get-task-response.json';assert frozen.read_bytes()==copy.read_bytes()
compiler=json.loads((base/'phase-1/compatibility/legacy-compile-manifest.json').read_text())
with tarfile.open(base/'phase-1/compatibility/legacy-compile-sdk.tar.gz') as archive:
 for entry in compiler:
  data=archive.extractfile('legacy-compile-sdk/'+Path(entry['frozen']).name).read();assert hashlib.sha256(data).hexdigest()==entry['sha256']
constant=root/'spec/src/main/java/org/a2aproject/sdk/spec/AgentInterface.java'
assert re.search(r'CURRENT_PROTOCOL_VERSION\s*=\s*"1\.0"',constant.read_text())
subprocess.run(['git','-c','core.fsmonitor=false','diff','--check'],cwd=root,check=True,capture_output=True)
links=0
for md in (base/'phase-1').rglob('*.md'):
 for match in re.finditer(r'(?<!!)\[[^\]]*\]\(([^)]+)\)',md.read_text()):
  target=match.group(1).split('#',1)[0]
  target=re.sub(r':\d+$', '', target)
  if not target or '://' in target:continue
  reference_base = base/'phase-1' if md.parent in {ev/'pre-correction', ev/'pre-resume'} else md.parent
  if md.name == 'compatibility-README.md' and md.parent == ev/'pre-resume': reference_base=base/'phase-1/compatibility'
  path=Path(target) if target.startswith('/') else reference_base/target
  assert path.exists() or path.resolve() == (ev/'final-integrity.json').resolve(),(md,target);links+=1
record={'checked_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'branch':git('branch','--show-current'),'HEAD':expected,'index_clean':True,'phase0_and_authoritative_inputs_unchanged':True,'fixture_hashes_checked':len(manifest['files']),'phase1_fixture_hashes_checked':len(prov['files']),'frozen_response_byte_identity':True,'old_sdk_archive_hashes_checked':len(compiler),'CURRENT_PROTOCOL_VERSION':'1.0','tracked_whitespace_check':'passed','phase1_local_markdown_links_checked':links,'archived_document_links_use_original_base':True,'errors':[]}
ev.mkdir(exist_ok=True);(ev/'final-integrity.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
