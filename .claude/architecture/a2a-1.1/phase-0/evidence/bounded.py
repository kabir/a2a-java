import datetime, json, os, signal, subprocess, sys, time
from pathlib import Path
seconds=int(sys.argv[1]); label=sys.argv[2]; command=sys.argv[3:]
root=Path('/tmp/a2a-phase0-run'); start=time.monotonic()
meta={'command':command,'cwd':os.getcwd(),'start':datetime.datetime.now(datetime.timezone.utc).isoformat(),'deadline_seconds':seconds}
with (root/(label+'.log')).open('wb') as log:
 p=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 meta['pid']=p.pid; (root/(label+'.json')).write_text(json.dumps(meta,indent=2))
 try:
  code=p.wait(timeout=seconds); meta['timed_out']=False
 except subprocess.TimeoutExpired:
  meta['timed_out']=True; os.killpg(p.pid,signal.SIGTERM)
  try: code=p.wait(timeout=25)
  except subprocess.TimeoutExpired:
   os.killpg(p.pid,signal.SIGKILL); code=p.wait(timeout=5)
 meta.update(exit_code=code,elapsed_seconds=round(time.monotonic()-start,3),end=datetime.datetime.now(datetime.timezone.utc).isoformat())
 (root/(label+'.json')).write_text(json.dumps(meta,indent=2)); print(json.dumps(meta),flush=True)
sys.exit(124 if meta['timed_out'] else code)
