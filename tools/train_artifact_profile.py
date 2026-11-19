#!/usr/bin/env python3
"""Compare preserved release JARs on the same actual-server workload without swapping dev outputs.
No old worlds are reset/refilled. Uses the existing finite TrainServerBenchmark in each artifact.
"""
import argparse,os,subprocess,time,hashlib,shutil
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('jar',type=Path);p.add_argument('fixture');p.add_argument('--mode',choices=['measure','verify'],default='measure');p.add_argument('--processors',type=int,default=4);a=p.parse_args()
root=Path(__file__).resolve().parents[1];jar=a.jar.resolve();folder=root/'run-benchmark'/a.fixture
import re
if not re.fullmatch(r'trains-v0311-[a-z0-9-]+',a.fixture) or not jar.is_file():raise SystemExit('Explicit 0.31.1 fixture and existing release JAR required')
marker=folder/'train-benchmark.marker';text='FactoryCraft train workload v1\n'
if folder.is_symlink() or folder.exists() and any(folder.iterdir()) and (not marker.is_file() or marker.read_text()!=text):raise SystemExit('Unmarked fixture')
if a.mode=='measure' and (folder/'world/level.dat').exists():raise SystemExit('Existing world must not be reseeded')
if a.mode=='verify' and not (folder/'checkpoint.nbt').is_file():raise SystemExit('Missing original checkpoint')
folder.mkdir(parents=True,exist_ok=True);marker.write_text(text);(folder/'eula.txt').write_text('eula=true\n')
props=folder/'server.properties'
if not props.exists():props.write_text('level-name=world\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:the_void"}\ngenerate-structures=false\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25588\nspawn-protection=0\nspawn-monsters=false\nspawn-animals=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\n')
mods=folder/'mods';mods.mkdir(exist_ok=True);link=mods/jar.name
if link.exists() and hashlib.sha256(link.read_bytes()).digest()!=hashlib.sha256(jar.read_bytes()).digest():raise SystemExit('Different artifact already installed')
if not link.exists():shutil.copyfile(jar,link)
if len(list(mods.iterdir()))!=1:raise SystemExit('Unexpected additional mods')
java=Path(os.environ['JAVA_HOME'])/'bin/java';md=root/'build/moddev'
cp=os.pathsep.join([str(md/'artifacts/neoforge-21.1.252.jar'),*(md/'serverLegacyClasspath.txt').read_text().splitlines()])
cmd=[str(java),f'-XX:ActiveProcessorCount={a.processors}',f'-Dfactorycraft.trainBenchmark.fixture={a.fixture}',f'-Dfactorycraft.trainBenchmark.mode={a.mode}','@'+str(md/'serverRunVmArgs.txt'),'-Xms512m','-Xmx2G','-cp',cp,'@'+str(md/'serverRunProgramArgs.txt')]
if '--nogui' not in (md/'serverRunProgramArgs.txt').read_text():cmd.append('--nogui')
started=time.time_ns()
code=subprocess.call(cmd,cwd=folder)
import json
report=folder/'reports'/f'{a.mode}.json'
if code!=0 or not report.is_file() or report.stat().st_mtime_ns<started or json.loads(report.read_text())['status']!='PASS':raise SystemExit('Workload failed; retained world/report: '+str(folder))

(folder/'reports'/f'artifact-{a.mode}.json').write_text(json.dumps(dict(jar=str(jar),sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),processors=a.processors,mode=a.mode,online_clients=0),indent=2)+'\n')
