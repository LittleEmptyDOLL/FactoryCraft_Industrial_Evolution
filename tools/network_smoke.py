#!/usr/bin/env python3
"""Opt-in real-client QA launcher. Run Gradle prepare tasks first; never touches ordinary saves.

JAVA_HOME=... python tools/network_smoke.py server
DISPLAY=:99 ... python tools/network_smoke.py client --name FactoryOne
DISPLAY=:100 ... python tools/network_smoke.py client --name FactoryTwo
"""
import argparse, os, shutil
from pathlib import Path

PROJECT=Path(__file__).resolve().parents[1]
ROOT=PROJECT/'run-gametest/network-v0130'
MARKER='FactoryCraft isolated network QA v1\n'

def prepare():
    marker=ROOT/'factorycraft-network.marker'
    if ROOT.is_symlink() or ROOT.exists() and any(ROOT.iterdir()) and (not marker.is_file() or marker.read_text()!=MARKER):
        raise SystemExit('Refusing unmarked/symlink network QA directory.')
    ROOT.mkdir(parents=True,exist_ok=True);marker.write_text(MARKER)
    server=ROOT/'server';server.mkdir(exist_ok=True)
    (server/'eula.txt').write_text('eula=true\n')
    properties=server/'server.properties'
    if not properties.exists():properties.write_text(('''level-name=world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:the_void"}
generate-structures=false
online-mode=false
enforce-secure-profile=false
server-ip=127.0.0.1
server-port=25585
spawn-protection=0
spawn-monsters=false
spawn-animals=false
view-distance=3
simulation-distance=3
max-players=4
max-tick-time=120000
''').replace('server-port=25585','server-port='+str(PORT)))

p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['server','client']);p.add_argument('--name',choices=['FactoryOne','FactoryTwo'],default='FactoryOne');p.add_argument('--fixture',choices=['network-v0130','network-v0301'],default='network-v0130');p.add_argument('--optional-mods',action='store_true');args=p.parse_args()
ROOT=PROJECT/'run-gametest'/args.fixture
PORT=25586 if args.fixture=='network-v0301' else 25585
prepare();kind='server' if args.mode=='server' else 'client'
vm=PROJECT/f'build/moddev/{kind}RunVmArgs.txt';program=PROJECT/f'build/moddev/{kind}RunProgramArgs.txt'
if not vm.is_file() or not program.is_file():raise SystemExit('Run ./gradlew writeClientLegacyClasspath writeServerLegacyClasspath prepareClientRun prepareServerRun first.')
if '--quickPlaySingleplayer' in program.read_text():raise SystemExit('Prepare client without -PsmokeWorld; normal saves must not open.')
java=Path(os.environ['JAVA_HOME'])/'bin/java' if 'JAVA_HOME' in os.environ else shutil.which('java')
if not java:raise SystemExit('Java 21 required.')
folder=ROOT/('server' if args.mode=='server' else 'client-'+args.name);folder.mkdir(exist_ok=True)
if args.optional_mods:
    cache=Path(os.environ.get('GRADLE_USER_HOME',str(PROJECT.parent/'gradle-home')))/'caches/modules-2/files-2.1'
    jars=list((PROJECT/'build/optional-runtime').glob('*.jar'))
    for group,artifact,version in [('maven.modrinth','jade','15.10.6+neoforge'),('mezz.jei','jei-1.21.1-neoforge','19.21.1.248'),('dev.emi','emi-neoforge','1.1.24+1.21.1')]:
        matches=[p for p in (cache/group/artifact/version).glob('*/*.jar') if '-sources' not in p.name]
        if len(matches)!=1:raise SystemExit('Prepare/download the pinned optional runtime first: '+artifact)
        jars+=matches
    mods=folder/'mods';mods.mkdir(exist_ok=True)
    for jar in jars:
        link=mods/jar.name
        if not link.exists():link.symlink_to(jar.resolve())

if args.mode=='client':
    options=folder/'options.txt'
    if not options.exists():
        original=PROJECT/'run/options.txt';lines=original.read_text().splitlines() if original.exists() else []
        values={line.partition(':')[0]:line.partition(':')[2] for line in lines if ':' in line}
        values.update(renderDistance='3',simulationDistance='3',maxFps='10',guiScale='2',pauseOnLostFocus='false',soundCategory_master='0.0',fullscreen='false',lang='ru_ru')
        if args.fixture=='network-v0301':values.update(maxFps='30',soundCategory_master='0.7',lang='en_us' if args.name=='FactoryOne' else 'ru_ru')
        options.write_text(''.join(k+':'+v+'\n' for k,v in values.items()))
modfolders='factorycraft%%'+str(PROJECT/'build/classes/java/main')+':factorycraft%%'+str(PROJECT/'build/resources/main')
properties=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
game=PROJECT/('build/moddev/artifacts/neoforge-'+properties['neo_version']+'.jar')
classpath=os.pathsep.join([str(game),str(PROJECT/'build/classes/java/main'),str(PROJECT/'build/resources/main'),*(PROJECT/f'build/moddev/{kind}LegacyClasspath.txt').read_text().splitlines()])
command=[str(java),'-Dfml.modFolders='+modfolders,'@'+str(vm),'-Xmx'+('1G' if args.mode=='server' else '1280M'),'-cp',classpath,'@'+str(program)]
if args.mode=='server':
    if '--nogui' not in program.read_text():command.append('--nogui')
else:command.extend(['--username',args.name,'--quickPlayMultiplayer','127.0.0.1:'+str(PORT),'--width','1280' if args.fixture=='network-v0301' else '854','--height','720' if args.fixture=='network-v0301' else '480'])
os.chdir(folder);os.execv(str(java),command)
