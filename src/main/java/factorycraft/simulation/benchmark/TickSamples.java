package factorycraft.simulation.benchmark;

import java.util.Arrays;

/** Fixed allocation during measurement; nearest-rank percentiles are calculated only after it. */
public final class TickSamples {
    public record Summary(int count,double meanMs,double p50Ms,double p95Ms,double p99Ms,double maxMs,int over50Ms) {}
    private final long[] samples;
    private int size;
    public TickSamples(int capacity) { if(capacity<1)throw new IllegalArgumentException("Empty window");samples=new long[capacity]; }
    public void add(long nanos) {
        if(nanos<0 || size==samples.length)throw new IllegalArgumentException("Invalid or full sample window");
        samples[size++]=nanos;
    }
    public int size() { return size; }
    public Summary summarize() {
        if(size==0)throw new IllegalStateException("No samples");
        long[] sorted=Arrays.copyOf(samples,size);Arrays.sort(sorted);double sum=0;int slow=0;
        for(long n:sorted) { sum+=n;if(n>50_000_000)slow++; }
        return new Summary(size,sum/size/1e6,percentile(sorted,.50),percentile(sorted,.95),percentile(sorted,.99),sorted[size-1]/1e6,slow);
    }
    private static double percentile(long[] sorted,double fraction) { return sorted[(int)Math.ceil(sorted.length*fraction)-1]/1e6; }
}
