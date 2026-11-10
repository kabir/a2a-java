from pathlib import Path
import xml.etree.ElementTree as E,json,hashlib,shutil
root=Path.cwd(); dest=Path('/tmp/a2a-phase1-run/legacy-compile-sdk');dest.mkdir(exist_ok=True)
p=E.parse('client/base/target/surefire-reports/TEST-org.a2aproject.sdk.client.ClientBuilderVersionTest.xml')
cp=next(x.get('value') for x in p.findall('.//property') if x.get('name')=='java.class.path')
legacy=[];candidate=[];entries=[]
for part in cp.split(':'):
 path=Path(part)
 if path.name=='test-classes': continue
 candidate.append(str(path))
 if str(path).startswith(str(root)) and part.endswith('/target/classes'):
  pom=E.parse(path.parent.parent/'pom.xml'); ns={'m':'http://maven.apache.org/POM/4.0.0'}
  artifact=pom.findtext('m:artifactId',namespaces=ns)
  old=Path('/Users/kabir/.m2/repository/org/a2aproject/sdk')/artifact/'1.5.1.Final-SNAPSHOT'/f'{artifact}-1.5.1.Final-SNAPSHOT.jar'
  assert old.exists(),old
  out=dest/old.name;shutil.copyfile(old,out)
  entries.append({'artifact':artifact,'path':str(old),'frozen':str(out),'sha256':hashlib.sha256(out.read_bytes()).hexdigest()})
  legacy.append(str(out))
 else: legacy.append(str(path))
Path('/tmp/a2a-phase1-run/legacy-classpath.txt').write_text(':'.join(legacy))
Path('/tmp/a2a-phase1-run/candidate-classpath.txt').write_text(':'.join(candidate))
Path('/tmp/a2a-phase1-run/legacy-compile-manifest.json').write_text(json.dumps(entries,indent=2))
print('Frozen',len(entries),'unchanged installed SDK jars for legacy compilation; candidate targets separate')
