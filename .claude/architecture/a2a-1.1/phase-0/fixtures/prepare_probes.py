#!/usr/bin/env python3
import json,pathlib
root=pathlib.Path(__file__).resolve().parent
manifest=json.loads((root/'manifest.json').read_text());probes=[]
for version in ['1.0','0.3']:
    old=version=='0.3';suffix='_v0_3' if old else ''
    domain='org.a2aproject.sdk.'+('compat03.' if old else '')+'spec.'
    wrapper=domain if old else 'org.a2aproject.sdk.jsonrpc.common.wrappers.'
    def source(binding,group,name):
        return 'raw/'+version+'/'+binding+'/'+group+suffix+'/'+name+'.txt'
    def add(id,raw,typ,selector=[],proto=''):
        text=(root/raw).read_text().lstrip()
        if text.startswith('data: '):text=text[6:]
        obj=json.loads(text)
        for key in selector:obj=obj[key]
        def fixed_status(value):
            if isinstance(value,dict):
                if isinstance(value.get('status'),dict) and 'state' in value['status']:
                    value['status'].setdefault('timestamp','2026-01-01T00:00:00Z')
                for child in value.values():fixed_status(child)
            elif isinstance(value,list):
                for child in value:fixed_status(child)
        fixed_status(obj)
        inp='inputs/'+version+'/'+id+'.json';f=root/inp;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(json.dumps(obj,indent=2)+'\n')
        probes.append({'id':version+'/'+id,'version':version,'input':inp,'class':typ+suffix,'proto_method':proto,'source_fixture':raw,'selector':selector,'derivation':'Remove leading data: for one SSE event; parse JSON; select keys listed; add fixed 2026-01-01T00:00:00Z timestamp to each status object only where absent (historical constructors otherwise use current time); pretty-print JSON'})
    for name,typ in [('SEND_MESSAGE_TEST_REQUEST','SendMessageRequest'),('SEND_MESSAGE_TEST_RESPONSE','SendMessageResponse'),('GET_TASK_TEST_REQUEST','GetTaskRequest'),('GET_TASK_TEST_RESPONSE','GetTaskResponse'),('CANCEL_TASK_TEST_REQUEST','CancelTaskRequest'),('CANCEL_TASK_TEST_RESPONSE','CancelTaskResponse')]:
        if old and name=='SEND_MESSAGE_TEST_REQUEST':continue # Historical raw fixture has trailing comma; retained, not repaired.
        add('rpc-'+name,source('jsonrpc','JsonMessages',name),wrapper+typ)
    # Historic application error is deliberately frozen, even where the old client had normalization quirks.
    add('rpc-error',source('jsonrpc','JsonMessages','SEND_MESSAGE_ERROR_TEST_RESPONSE'),wrapper+('JSONRPCErrorResponse' if old else 'A2AErrorResponse'))
    for binding in ['jsonrpc','rest']:
        raw=source(binding,'JsonMessages' if binding=='jsonrpc' else 'JsonRestMessages','SEND_MESSAGE_TEST_REQUEST')
        if not (old and binding=='jsonrpc'):add(binding+'-send-params',raw,domain+'MessageSendParams',['params'] if binding=='jsonrpc' else [],'sendMessageRequest')
        raw=source(binding,'JsonMessages' if binding=='jsonrpc' else 'JsonRestMessages','GET_TASK_TEST_RESPONSE')
        add(binding+'-task',raw,domain+'Task',['result'] if binding=='jsonrpc' else [],'task')
    for name,typ,method,key in [('TASK','Task','task','task'),('MESSAGE','Message','message','message'),('STATUS_UPDATE','TaskStatusUpdateEvent','taskStatusUpdateEvent','statusUpdate'),('STATUS_UPDATE_EVENT_FINAL','TaskStatusUpdateEvent','taskStatusUpdateEvent','statusUpdate'),('ARTIFACT_UPDATE','TaskArtifactUpdateEvent','taskArtifactUpdateEvent','artifactUpdate')]:
        const='STREAMING_'+name+('_EVENT' if name!='STATUS_UPDATE_EVENT_FINAL' else '')
        selector=['result']+([] if old else [key])
        add('event-'+name.lower(),source('jsonrpc','JsonStreamingMessages',const),domain+typ,selector,method)
(root/'probes.json').write_text(json.dumps(probes,indent=2)+'\n');print('Prepared',len(probes),'probes')
