#!/usr/bin/env python3
"""Fetch immutable official historical definitions through GitHub CLI, read-only."""
import hashlib,json,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parent
sources=[('1.0','v1.0.0','173695755607e884aa9acf8ce4feed90e32727a1','specification/a2a.proto'),('0.3','v0.3.0','8d57eba286de756176892518a8fc39b0ac2ccefb','specification/grpc/a2a.proto'),('0.3','v0.3.0','8d57eba286de756176892518a8fc39b0ac2ccefb','specification/json/a2a.json')]
entries=[]
for version,tag,commit,path in sources:
 data=subprocess.check_output(['gh','api','-H','Accept: application/vnd.github.raw+json','repos/a2aproject/A2A/contents/'+path+'?ref='+commit],timeout=60)
 out=root/'upstream'/version/path;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
 entries.append({'file':str(out.relative_to(root)),'sha256':hashlib.sha256(data).hexdigest(),'upstream_tag':tag,'upstream_commit':commit,'source_path':path,'url':'https://github.com/a2aproject/A2A/blob/'+commit+'/'+path,'recipe':'gh api -H "Accept: application/vnd.github.raw+json" "repos/a2aproject/A2A/contents/'+path+'?ref='+commit+'"'})
(root/'upstream-manifest.json').write_text(json.dumps(entries,indent=2)+'\n')
print('Fetched',len(entries),'official immutable historical definitions')
