package factorycraft.simulation.fluids;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Cached adjacency, sleeping edges, bounded FIFO work and a simultaneous reserve/drain/fill commit. */
public final class FluidTransport<K> {
    public static final class Node<K> {
        private final FluidTransport<K> owner;
        private final FluidCell<K> cell;
        private final BooleanSupplier eligible;
        private final ArrayList<Edge<K>> edges=new ArrayList<>(6);
        private long epoch=-1;
        private K startKey,reservedKey;
        private int available,space;
        private boolean removed,dirty,availableNow;
        private Node(FluidTransport<K> owner,FluidCell<K> cell,BooleanSupplier available) { this.owner=owner;this.cell=cell;this.eligible=available; }
    }
    private static final class Edge<K> {
        final Node<K> a,b;
        Node<K> from,to;
        int amount;
        boolean queued,removed;
        Edge(Node<K> a,Node<K> b) { this.a=a;this.b=b; }
    }
    private final ArrayDeque<Edge<K>> active=new ArrayDeque<>();
    private final ArrayList<Edge<K>> plans=new ArrayList<>();
    private final ArrayList<Node<K>> dirty=new ArrayList<>();
    private final Set<Node<K>> registry=Collections.newSetFromMap(new IdentityHashMap<>());
    private long epoch,visits,volume;
    private long revision,componentRevision=-1;
    private int componentCount;
    private int edgeCount,nodes;
    public Node<K> add(FluidCell<K> cell) { return add(cell,()->true); }
    public Node<K> add(FluidCell<K> cell,BooleanSupplier available) {
        var n=new Node<>(this,Objects.requireNonNull(cell),Objects.requireNonNull(available));cell.observe(()->wake(n));registry.add(n);nodes++;revision++;return n;
    }
    public void connect(Node<K> a,Node<K> b) {
        check(a);check(b);if(a==b)return;
        for(var edge:a.edges)if(edge.a==b || edge.b==b)return;
        var edge=new Edge<>(a,b);a.edges.add(edge);b.edges.add(edge);edgeCount++;revision++;enqueue(edge);
    }
    public void disconnect(Node<K> a,Node<K> b) {
        check(a);
        for(int i=a.edges.size()-1;i>=0;i--) {
            var edge=a.edges.get(i);
            if(edge.a==b || edge.b==b) { edge.removed=true;a.edges.remove(i);b.edges.remove(edge);edgeCount--;revision++; }
        }
    }
    public void remove(Node<K> node) {
        if(node==null || node.removed)return;check(node);
        while(!node.edges.isEmpty()) { var e=node.edges.getLast();disconnect(node,e.a==node ? e.b:e.a); }
        node.removed=true;node.cell.observe(null);registry.remove(node);nodes--;revision++;
    }
    public void wake(Node<K> n) {
        if(n.removed)return;
        for(int i=0;i<n.edges.size();i++)enqueue(n.edges.get(i));
    }
    private void check(Node<K> n) { if(n.owner!=this || n.removed)throw new IllegalArgumentException("Foreign/removed fluid node"); }
    private void enqueue(Edge<K> e) { if(!e.removed && !e.queued) { e.queued=true;active.addLast(e); } }
    private void prepare(Node<K> n) {
        if(n.epoch==epoch)return;
        n.epoch=epoch;n.availableNow=n.eligible.getAsBoolean();n.available=n.cell.amount();n.space=n.cell.space();n.startKey=n.cell.key();n.reservedKey=n.startKey;
    }
    private void mark(Node<K> n) { if(!n.dirty) { n.dirty=true;dirty.add(n); } }
    public int tick(int budget,int rate) {
        if(budget<0 || rate<1)throw new IllegalArgumentException("Invalid fluid work/rate limit");
        epoch++;int count=Math.min(budget,active.size()),work=0;
        while(work<count) {
            var edge=active.removeFirst();edge.queued=false;work++;visits++;
            if(edge.removed)continue;
            prepare(edge.a);prepare(edge.b);
            if(!edge.a.availableNow || !edge.b.availableNow) { enqueue(edge);continue; }
            long difference=(long)edge.a.cell.amount()*edge.b.cell.capacity()-(long)edge.b.cell.amount()*edge.a.cell.capacity();
            var from=difference>=0 ? edge.a:edge.b;var to=from==edge.a ? edge.b:edge.a;
            if(from.startKey==null || to.reservedKey!=null && !to.reservedKey.equals(from.startKey))continue;
            // Degree damping keeps simultaneous multi-edge flows from overshooting junction pressure.
            int degree=Math.max(1,Math.max(from.edges.size(),to.edges.size()));
            int equalizing=(int)(Math.abs(difference)/(((long)from.cell.capacity()+to.cell.capacity())*degree));
            int amount=Math.min(rate,Math.min(equalizing,Math.min(from.available,to.space)));
            if(amount==0)continue;
            from.available-=amount;to.space-=amount;to.reservedKey=from.startKey;
            edge.from=from;edge.to=to;edge.amount=amount;plans.add(edge);mark(from);mark(to);
        }
        // Newly received liquid cannot be relayed again in this frame. Every output was reserved first.
        for(int i=0;i<plans.size();i++) { var e=plans.get(i);e.from.cell.removeReserved(e.amount); }
        for(int i=0;i<plans.size();i++) { var e=plans.get(i);e.to.cell.addReserved(e.from.startKey,e.amount);volume+=e.amount; }
        RuntimeException failure=null;
        try {
            for(int i=0;i<dirty.size();i++) {
                var n=dirty.get(i);n.dirty=false;
                try { n.cell.notifyChanged(); }catch(RuntimeException error) {
                    if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);
                }
            }
        } finally { plans.clear();dirty.clear(); }
        if(failure!=null)throw failure;
        return work;
    }
    public int nodes() { return nodes; }
    public int edges() { return edgeCount; }
    public int pendingEdges() { return active.size(); }
    public long visits() { return visits; }
    public long volumeMoved() { return volume; }
    /** Diagnostic only: recomputed on request after topology changes, never in the tick loop. */
    public int components() {
        if(componentRevision==revision)return componentCount;
        var seen=Collections.newSetFromMap(new IdentityHashMap<Node<K>,Boolean>());var queue=new ArrayDeque<Node<K>>();int count=0;
        for(var node:registry)if(seen.add(node)) {
            count++;queue.add(node);
            while(!queue.isEmpty()) {
                var next=queue.removeFirst();
                for(var edge:next.edges) { var other=edge.a==next ? edge.b:edge.a;if(seen.add(other))queue.addLast(other); }
            }
        }
        componentRevision=revision;componentCount=count;return count;
    }
}
