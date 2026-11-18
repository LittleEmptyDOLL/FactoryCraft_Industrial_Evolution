import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;

/** Bounded warmed pure-kernel profile. Not Minecraft TPS, packets or a world simulation. */
public final class TrainKernelBenchmark {
  static long run(int count,int ticks,boolean report){
    var g=new RailGraph();var paths=new TrainPath[count];var parameters=new TrainMotion.Parameters(4000,1.5,16,.0075,10,1.2);
    for(int i=0;i<count;i++){
      var id=new UUID(0,i+1);g.add(new RailGraph.Edge(id,RailCurve.plan(new RailEnd(i*8,70,0,new RailHeading(0)),new RailEnd(i*8,70,-128,new RailHeading(0)))));paths[i]=new TrainPath(g,new RailGraph.Directed(id,false));
    }
    var v=new double[count];var p=new double[count];Arrays.fill(p,24);var fuel=new long[count];Arrays.fill(fuel,32_000_000);var spent=new long[count];
    var samples=new long[ticks];long reads=0;
    var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId(),allocated=bean.getThreadAllocatedBytes(thread);
    for(int tick=0;tick<ticks;tick++){
      long start=System.nanoTime();for(int i=0;i<count;i++){
        boolean powered=fuel[i]>=30000&&p[i]+TrainMotion.stoppingDistance(v[i],4000,16)+8<128;
        var next=TrainMotion.tick(v[i],parameters,powered,!powered);if(powered){fuel[i]-=30000;spent[i]+=30000;}
        p[i]+=next.distance();v[i]=next.speed();var pose=BogiePose.sample(paths[i],g,p[i],false);if(pose.center().y()!=70.125)throw new AssertionError("grade drift");reads++;
        if(fuel[i]+spent[i]!=32_000_000)throw new AssertionError("fuel ledger");
      }samples[tick]=System.nanoTime()-start;
    }
    long bytes=bean.getThreadAllocatedBytes(thread)-allocated;long total=Arrays.stream(samples).sum();Arrays.sort(samples);
    if(report)System.out.printf(Locale.ROOT,"{\"profile\":\"pure-train-kernel\",\"trains\":%d,\"ticks\":%d,\"mean_ns\":%d,\"p95_ns\":%d,\"p99_ns\":%d,\"max_ns\":%d,\"allocated_bytes\":%d,\"bogie_samples\":%d,\"fuel_ledger\":\"exact\"}%n",count,ticks,total/ticks,samples[(int)(ticks*.95)],samples[(int)(ticks*.99)],samples[ticks-1],bytes,reads);
    return total;
  }
  public static void main(String[] args){for(int i=0;i<3;i++)run(32,400,false);for(int i=0;i<3;i++)run(64,1600,true);}
}
