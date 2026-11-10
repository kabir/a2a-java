#!/usr/bin/env python3
"""Reproduce historical fixtures with javac's exact text-block decoding, no SDK/Maven."""
import hashlib,json,pathlib,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parent
COMMIT='2817ed78d02d9a6e609dafdf7f5129484edef017'
repo=pathlib.Path(sys.argv[1]).resolve()
paths=[]
for version in ['1.0','0.3']:
    prefix='' if version=='1.0' else 'compat-0.3/'
    pkg='org/a2aproject/sdk/' + ('' if version=='1.0' else 'compat03/')+'client/transport/'
    suffix='' if version=='1.0' else '_v0_3'
    for binding,base in [('jsonrpc','JsonMessages'),('jsonrpc','JsonStreamingMessages'),('rest','JsonRestMessages')]:
        path=prefix+'client/transport/'+binding+'/src/test/java/'+pkg+binding+'/'+base+suffix+'.java'
        paths.append((version,binding,path))
manifest={'commit':COMMIT,'source_repository':'https://github.com/a2aproject/a2a-java','extraction':'git show COMMIT:PATH; javac unmodified Java fixture constants; reflection obtains exact runtime text-block values','files':[]}
classes=[]
for version,binding,path in paths:
    data=subprocess.check_output(['git','-C',str(repo),'show',COMMIT+':'+path],timeout=60)
    out=ROOT/'sources'/path;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
    package=data.decode().split('package ',1)[1].split(';',1)[0]
    cls=package+'.'+out.stem;classes.append((version,binding,path,cls))
    manifest['files'].append({'file':str(out.relative_to(ROOT)),'sha256':hashlib.sha256(data).hexdigest(),'source_path':path,'source_commit':COMMIT,'kind':'unmodified historical source'})
for path in ['spec-grpc/src/main/proto/a2a.proto','compat-0.3/spec-grpc/src/main/proto/a2a.proto']:
    data=subprocess.check_output(['git','-C',str(repo),'show',COMMIT+':'+path],timeout=60);out=ROOT/'sources'/path;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
    manifest['files'].append({'file':str(out.relative_to(ROOT)),'sha256':hashlib.sha256(data).hexdigest(),'source_path':path,'source_commit':COMMIT,'kind':'historical locally adapted upstream proto; upstream tag declared in source'})
helper=ROOT/'ExtractConstants.java'
helper.write_text('''import java.nio.file.*;import java.lang.reflect.*;public class ExtractConstants {public static void main(String[] args)throws Exception{Path dir=Path.of(args[1]);Files.createDirectories(dir);for(Field f:Class.forName(args[0]).getDeclaredFields()){if(f.getType()!=String.class||!Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);Files.writeString(dir.resolve(f.getName()+".txt"),(String)f.get(null));System.out.println(f.getName());}}}''')
with tempfile.TemporaryDirectory(prefix='a2a-history-compile-') as tmp:
    subprocess.run(['javac','-d',tmp,str(helper)]+[str(ROOT/'sources'/p) for _,_,p,_ in classes],check=True,timeout=60)
    for version,binding,path,cls in classes:
        dest=ROOT/'raw'/version/binding/pathlib.Path(path).stem
        names=subprocess.check_output(['java','-cp',tmp,'ExtractConstants',cls,str(dest)],text=True,timeout=60).splitlines()
        for name in names:
            f=dest/(name+'.txt');data=f.read_bytes()
            try:json.loads(data);kind='JSON'
            except ValueError:kind='SSE stream text'
            manifest['files'].append({'file':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(data).hexdigest(),'source_path':path,'source_commit':COMMIT,'constant':name,'kind':kind})
(ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Archived',len(manifest['files']),'files')
