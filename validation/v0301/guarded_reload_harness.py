from pathlib import Path
import os,subprocess,sys,threading,time,gzip
root=Path('/workspace/work/FactoryCraft');log=root/'validation/v0301/network-guarded-reload.log';fifo=Path('/tmp/v0301-console.fifo')
if fifo.exists():fifo.unlink()
os.mkfifo(fifo)
proc=subprocess.Popen([sys.executable,'tools/network_smoke.py','server','--fixture','network-v0301','--optional-mods'],cwd=root,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
lock=threading.Lock()
def send(text):
 with lock:proc.stdin.write(text+'\n');proc.stdin.flush()
def commands():
 with os.fdopen(os.open(fifo,os.O_RDWR),'r') as f:
  for line in f:send(line.rstrip('\n'))
threading.Thread(target=commands,daemon=True).start();ready=False
with log.open('w') as out:
 for line in proc.stdout:
  out.write(line);out.flush()
  if not ready and 'Done (' in line and 'For help' in line:
   ready=True;send('tick freeze');print('Server ready; freeze queued before joining clients',flush=True)
print('Exit',proc.wait(),flush=True)
