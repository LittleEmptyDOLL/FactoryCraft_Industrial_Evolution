#!/usr/bin/env python3
"""Bounded 0.25.0 / 0.25.1 geometry comparison in independent warmed JVMs.
Prepared terrain clips natural solids. No world/BE writes; native GameTests report those separately.
"""
import os, pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[1]
java = pathlib.Path(os.environ.get('JAVA_HOME', '/workspace/work/jdk21')) / 'bin'
base = '1132d39689c24ae5df46091076ece4559f875bdd'
common = r'''import factorycraft.simulation.resources.*;import java.util.*;import java.lang.management.ManagementFactory;
public class ResourceProfile {
 static int ground(int mode,int x,int center){return 70+(mode==1?Math.floorDiv(x-center,4):mode==2 && x>center?10:0);}
 public static void main(String[]args) {
 var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 for(int radius:new int[]{20,48})for(int mode=0;mode<3;mode++) {
 int depth=radius==48?64:8;var s=SETTINGS;var p=ResourceVeins.patch(42,19,0,0,s);long[] time=new long[30],alloc=new long[30];long count=0,sum=0;
 for(int run=-60;run<30;run++) {
 long bytes=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();count=0;sum=0;
 PREPARE
 for(int x=p.x()-radius;x<=p.x()+radius;x++)for(int z=p.z()-radius;z<=p.z()+radius;z++) {
 COLUMN
 for(int y=70-BOTTOM;y<=Math.min(70+TOP,ground(mode,x,p.x()));y++) {int stock=SAMPLE;if(stock>0){count++;sum+=stock;}}
 }
 if(run>=0){time[run]=System.nanoTime()-start;alloc[run]=bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes;}
 }
 long total=Arrays.stream(time).sum();Arrays.sort(time);
 System.out.printf(Locale.ROOT,"{\"version\":\"LABEL\",\"radius\":%d,\"verticalParameter\":%d,\"terrain\":%d,\"cells\":%d,\"checksum\":%d,\"meanNs\":%.1f,\"medianNs\":%d,\"p95Ns\":%d,\"p99Ns\":%d,\"maxNs\":%d,\"allocationMeanBytes\":%.1f}%n",radius,DEPTH_VALUE,mode,count,sum,total/30.0,time[15],time[28],time[29],time[29],Arrays.stream(alloc).average().orElse(0));
 }}}
'''
for label in ['0.25.0', '0.25.1']:
    with tempfile.TemporaryDirectory(prefix='factorycraft-resource-profile-') as directory:
        directory = pathlib.Path(directory)
        source = directory / 'factorycraft/simulation/resources/ResourceVeins.java'
        source.parent.mkdir(parents=True)
        source.write_bytes(subprocess.check_output(['git', 'show', base+':src/main/java/factorycraft/simulation/resources/ResourceVeins.java'], cwd=root) if label=='0.25.0' else (root/'src/main/java/factorycraft/simulation/resources/ResourceVeins.java').read_bytes())
        files = [source]
        if label=='0.25.0':
            window = source.with_name('SurfaceWindow.java')
            window.write_bytes(subprocess.check_output(['git','show',base+':src/main/java/factorycraft/simulation/resources/SurfaceWindow.java'],cwd=root))
            files.append(window)
        text = common.replace('LABEL', label)
        text = text.replace('SETTINGS', 'new ResourceVeins.Settings(radius,radius,500,5000,16,'+('radius==48?64:12,.12)' if label=='0.25.0' else 'depth,depth,.12)'))
        text = text.replace('PREPARE', 'final int terrain=mode;var window=new SurfaceWindow(p.x()-radius,p.z()-radius,2*radius+1,2*radius+1,8,(x,z)->ground(terrain,x,p.x()));' if label=='0.25.0' else 'var body=p.atHeight(70);')
        text = text.replace('TOP', '16' if label=='0.25.0' else 'depth')
        text = text.replace('DEPTH_VALUE', '(radius==48?64:12)' if label=='0.25.0' else 'depth')
        text = text.replace('BOTTOM', '(radius==48?64:12)' if label=='0.25.0' else 'depth')
        text = text.replace('COLUMN', 'var column=p.column(x,z);int top=window.height(x,z);' if label=='0.25.0' else 'var column=body.column(x,z);')
        text = text.replace('SAMPLE', 'column.stock(top-y)' if label=='0.25.0' else 'column.stock(y)')
        (directory/'ResourceProfile.java').write_text(text)
        subprocess.run([str(java/'javac'),'-d',str(directory),*map(str, files),str(directory/'ResourceProfile.java')],check=True)
        subprocess.run([str(java/'java'),'-Xms128m','-Xmx256m','-cp',str(directory),'ResourceProfile'],check=True)
