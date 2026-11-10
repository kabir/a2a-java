#!/usr/bin/env python3
"""Run unchanged harness against a supplied current SDK runtime classpath."""
import argparse,json,pathlib,subprocess,tempfile,sys
root=pathlib.Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--version',choices=['1.0','0.3'],required=True);p.add_argument('--classpath-file',required=True);p.add_argument('--capture',help='Capture output to a NEW directory; this never overwrites frozen expectations');args=p.parse_args()
cp=pathlib.Path(args.classpath_file).read_text().strip()
with tempfile.TemporaryDirectory(prefix='a2a-wire-probe-') as tmp:
    subprocess.run(['javac','-cp',cp,'-d',tmp,str(root/'WireProbe.java')],check=True,timeout=60)
    output=pathlib.Path(args.capture).resolve() if args.capture else pathlib.Path(tmp)/'actual'
    if args.capture and output.exists():sys.exit('Capture destination already exists; refusing to overwrite')
    subprocess.run(['java','-cp',tmp+':'+cp,'WireProbe',str(root),args.version,str(output)],check=True,timeout=60)
    if not args.capture:
        expected=root/'expected'/args.version;actual=output/args.version
        files=sorted(expected.iterdir());assert files,'No frozen expectations'
        failures=[]
        for f in files:
            other=actual/f.name
            if not other.exists() or (json.loads(f.read_text())!=json.loads(other.read_text()) if f.suffix=='.json' else f.read_bytes()!=other.read_bytes()):failures.append(f.name)
        extras=set(x.name for x in actual.iterdir())-set(x.name for x in files)
        if failures or extras:sys.exit('REGRESSION: changed/missing='+repr(failures)+' extra='+repr(sorted(extras)))
        known=sum(f.name.endswith('.error.json') for f in files)
        print('PASS:',len(files)-known,'wire comparisons;',known,'known-failure fingerprints')
