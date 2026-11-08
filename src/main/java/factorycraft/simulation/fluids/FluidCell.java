package factorycraft.simulation.fluids;

import java.util.Objects;

/** Integer volumes, one immutable fluid identity per cell. Simulation never mutates storage. */
public final class FluidCell<K> {
    private final int capacity;
    private final Runnable changed;
    private Runnable observer;
    private K key;
    private int amount;
    public FluidCell(int capacity,Runnable changed) {
        if(capacity<1)throw new IllegalArgumentException("Nonpositive fluid capacity");
        this.capacity=capacity;this.changed=Objects.requireNonNull(changed);
    }
    public int capacity() { return capacity; }
    public int amount() { return amount; }
    public K key() { return key; }
    public int space() { return Math.max(0,capacity-amount); }
    public boolean accepts(K fluid) { return amount==0 || Objects.equals(key,fluid); }
    public int fill(K fluid,int offered,boolean simulate) {
        Objects.requireNonNull(fluid);
        if(offered<=0 || !accepts(fluid))return 0;
        int accepted=Math.min(offered,space());
        if(accepted>0 && !simulate) { addReserved(fluid,accepted);notifyChanged(); }
        return accepted;
    }
    public int drain(int requested,boolean simulate) {
        int drained=Math.min(Math.max(0,requested),amount);
        if(drained>0 && !simulate) { removeReserved(drained);notifyChanged(); }
        return drained;
    }
    /** Both stores commit before either callback; no temporary unowned volume. */
    public int transferTo(FluidCell<K> target,int limit,boolean simulate) {
        Objects.requireNonNull(target);
        if(target==this || amount==0 || limit<=0 || !target.accepts(key))return 0;
        int moved=Math.min(limit,Math.min(amount,target.space()));
        if(moved>0 && !simulate) {
            K fluid=key;removeReserved(moved);target.addReserved(fluid,moved);
            try { notifyChanged(); }finally { target.notifyChanged(); }
        }
        return moved;
    }
    /** Recipe conversion commits both stores before either callback; no intermediate unowned fluid. */
    public boolean convertTo(FluidCell<K> target,int consumed,K output,int produced,boolean simulate) {
        Objects.requireNonNull(target);Objects.requireNonNull(output);
        if(target==this || consumed<1 || produced<1 || amount<consumed || target.space()<produced || !target.accepts(output))return false;
        if(!simulate) {
            removeReserved(consumed);target.addReserved(output,produced);
            try { notifyChanged(); }finally { target.notifyChanged(); }
        }
        return true;
    }
    /** Oversized legacy saves retain volume; further filling waits until overflow is drained. */
    public void restore(K fluid,int volume) {
        if(volume<0 || volume>0 && fluid==null)throw new IllegalArgumentException("Invalid persisted fluid");
        amount=volume;key=volume==0 ? null:fluid;
    }
    void observe(Runnable callback) {
        if(callback!=null && observer!=null)throw new IllegalStateException("Fluid cell already belongs to a graph");
        observer=callback;
    }
    void removeReserved(int volume) {
        if(volume<0 || volume>amount)throw new IllegalStateException("Fluid reservation exceeded source");
        amount-=volume;if(amount==0)key=null;
    }
    void addReserved(K fluid,int volume) {
        if(volume<0 || volume>space() || !accepts(fluid))throw new IllegalStateException("Fluid reservation exceeded destination");
        if(volume>0) { key=fluid;amount+=volume; }
    }
    void notifyChanged() { try { changed.run(); }finally { if(observer!=null)observer.run(); } }
}
