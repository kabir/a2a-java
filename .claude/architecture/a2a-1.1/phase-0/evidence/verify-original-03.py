from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,tarfile
root=Path('/tmp/a2a-phase0-deliverable')
archive=root/'evidence/unverified-0.3-capture.tar.gz'
actual=Path('/tmp/a2a-phase0-03-followup/0.3')
expected={}
with tarfile.open(archive) as a:
    for member in a.getmembers():
        if member.isfile():
            name=Path(member.name).name
            assert name not in expected
            expected[name]=a.extractfile(member).read()
observed={p.name:p.read_bytes() for p in actual.iterdir() if p.is_file()}
assert expected.keys()==observed.keys(), 'Capture file sets differ'
failures=[]
for name,blob in expected.items():
    same=json.loads(blob)==json.loads(observed[name]) if name.endswith('.json') else blob==observed[name]
    if not same: failures.append(name)
assert not failures, failures
result={'checked_utc':datetime.now(timezone.utc).isoformat(),'authority':'Original pre-STOP capture archived before resumed verification; not expectations generated from candidate changes','expected_archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'files':len(expected),'positive_comparisons':sum(not n.endswith('.error.json') for n in expected),'known_failure_fingerprints':sum(n.endswith('.error.json') for n in expected),'comparison':'JSON semantic equality; protobuf binary byte equality; exact expected/actual file set','failures':failures,'scope':'Selected historical codec cases only, not complete lifecycle/binding conformance'}
(root/'evidence/historical-0.3-comparison.json').write_text(json.dumps(result,indent=2)+'\n')
dest=root/'fixtures/expected/0.3'
assert not dest.exists(), 'Refuse to replace frozen expectations'
dest.mkdir()
for name,blob in expected.items(): (dest/name).write_bytes(blob)
print(json.dumps(result))
