from pathlib import Path
import xml.etree.ElementTree as E,subprocess,hashlib,json
root=Path('/Users/kabir/.codex-api/worktrees/3053/a2a-java');run=Path('/tmp/a2a-phase1-run');dest=run/'itk-type-probe';dest.mkdir(exist_ok=True)
report=next((root/'client/base/target/surefire-reports').glob('TEST-*.xml'));props={p.get('name'):p.get('value') for p in E.parse(report).getroot().findall('properties/property')};classpath=props.get('surefire.test.class.path') or props['java.class.path'];(dest/'classpath.txt').write_text(classpath)
source=dest/'ItkManagedChannelTypeProbe.java';source.write_text('''import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcManagedChannelOptions;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
final class ItkManagedChannelTypeProbe {
    static void configure(ClientBuilder clientBuilder) {
        clientBuilder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder()
                .managedChannel(GrpcManagedChannelOptions.builder().usePlaintext().build()));
    }
}
''');command=['javac','--release','17','-proc:none','-cp',classpath,'-d',str(dest),str(source)];result=subprocess.run(command,cwd=root,text=True,capture_output=True);(dest/'compiler.log').write_text(result.stdout+result.stderr);assert result.returncode==0,(result.returncode,result.stdout,result.stderr)
record={'scope':'Selected type evidence for exact migrated shared ITK factory expression; NOT compilation of optional ITK module or its unavailable external proto inputs','java_release':17,'exit_code':result.returncode,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'class_sha256':hashlib.sha256((dest/'ItkManagedChannelTypeProbe.class').read_bytes()).hexdigest(),'classpath_sha256':hashlib.sha256(classpath.encode()).hexdigest(),'command':command};(dest/'result.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({k:v for k,v in record.items() if k!='command'},indent=2))
