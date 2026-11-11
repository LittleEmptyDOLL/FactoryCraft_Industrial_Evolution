#!/usr/bin/env python3
"""Compare original 0.24.1 geometry and 0.25 volumetric sampling in isolated JVMs.
No Minecraft/world writes. Native BE creation measurements are reported separately by GameTest.
"""
import json, os, pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
java=pathlib.Path(os.environ.get('JAVA_HOME','/workspace/work/jdk21'))/'bin'
base='dd7c81596e9db216fb10d75748ed9479ced1ea07'
common='''import factorycraft.simulation.resources.ResourceVeins;import java.util.*;import java.lang.management.ManagementFactory;
public class ResourceProfile { public static void main(String[]args) {
 var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 for(int radius:new int[]{20,48}) {int depth=radius==48?64:12;var s=SETTINGS;var p=ResourceVeins.patch(42,19,0,0,s);long[] time=new long[30],alloc=new long[30];long count=0,sum=0;
 for(int run=-80;run<30;run++) {long bytes=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();count=0;sum=0;
 for(int x=p.x()-radius;x<=p.x()+radius;x++)for(int z=p.z()-radius;z<=p.z()+radius;z++) {BODY}
 if(run>=0){time[run]=System.nanoTime()-start;alloc[run]=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes;}}
 Arrays.sort(time);System.out.printf(Locale.ROOT,"{\\\"version\\\":\\\"LABEL\\\",\\\"radius\\\":%d,\\\"targetDepth\\\":%d,\\\"cells\\\":%d,\\\"checksum\\\":%d,\\\"meanNs\\\":%.1f,\\\"medianNs\\\":%d,\\\"p95Ns\\\":%d,\\\"p99Ns\\\":%d,\\\"maxNs\\\":%d,\\\"allocationMeanBytes\\\":%.1f}%n",radius,depth,count,sum,Arrays.stream(time).average().orElse(0),time[15],time[28],time[29],time[29],Arrays.stream(alloc).average().orElse(0));
 } } }
'''
# Use ordinary JSON escaping in the generated Java string.
common=common.replace('\\\\\\"','\\"')
for label in ['0.24.1','0.25.0']:
 with tempfile.TemporaryDirectory(prefix='factorycraft-resource-profile-') as d:
  d=pathlib.Path(d);source=d/'factorycraft/simulation/resources/ResourceVeins.java';source.parent.mkdir(parents=True)
  source.write_bytes(subprocess.check_output(['git','show',base+':src/main/java/factorycraft/simulation/resources/ResourceVeins.java'],cwd=root) if label=='0.24.1' else (root/'src/main/java/factorycraft/simulation/resources/ResourceVeins.java').read_bytes())
  text=common.replace('LABEL',label)
  text=text.replace('SETTINGS','new ResourceVeins.Settings(radius,radius,500,5000,16'+(',depth,.12)' if label=='0.25.0' else ')'))
  text=text.replace('BODY','var c=p.column(x,z);for(int down=0;down<c.depth();down++){int stock=c.stock(down);if(stock>0){count++;sum+=stock;}}' if label=='0.25.0' else 'int stock=p.stock(x,z);if(stock>0){count++;sum+=stock;}')
  (d/'ResourceProfile.java').write_text(text)
  subprocess.run([str(java/'javac'),'-d',str(d),str(source),str(d/'ResourceProfile.java')],check=True)
  subprocess.run([str(java/'java'),'-Xms128m','-Xmx256m','-cp',str(d),'ResourceProfile'],check=True)
