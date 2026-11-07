package factorycraft.simulation.power;

import java.util.*;

/**
 * Loaded-node power topology with incremental, spatially indexed rebuilds. No Minecraft dependency.
 * Additions keep old valid networks online. Removing a pole immediately quarantines only its old
 * component until a replacement snapshot is ready; unrelated components continue to supply power.
 */
public final class PowerTopology<T> {
    public enum Kind { POLE, SOURCE, CONSUMER }
    @FunctionalInterface public interface Receiver { int receive(int offered,boolean simulate); }
    public static final class Node<T> {
        private final T value;
        private final PowerPoint point;
        private final Kind kind;
        private final Receiver receiver;
        private Node<T> previous,next;
        private boolean alive=true;
        private long visited;
        private Route<T> route;
        private Node(T value,PowerPoint p,Kind kind,Receiver receiver) { this.value=value;point=p;this.kind=kind;this.receiver=receiver; }
        public T value() { return value; }
    }
    private static final class Route<T> {
        final long generation;
        final int[] groups,cursors;
        final ArrayList<Node<T>> batch=new ArrayList<>(32);
        int[] batchGroups=new int[32];
        int group,entries;
        Route(Snapshot<T> snapshot,int[] groups,long generation) {
            this.generation=generation;this.groups=groups;cursors=new int[groups.length];
            long size=0;
            for(int root:groups)size+=snapshot.consumers.getOrDefault(root,List.of()).size();
            entries=(int)Math.min(Integer.MAX_VALUE,size);
        }
    }
    private static final class Snapshot<T> {
        final IdentityHashMap<Node<T>,Integer> poleGroups=new IdentityHashMap<>();
        final IdentityHashMap<Node<T>,int[]> memberships=new IdentityHashMap<>();
        final Map<Integer,List<Node<T>>> consumers=new HashMap<>();
        final Set<Integer> disabled=new HashSet<>();
        int networks;
        boolean suspended;
        boolean valid(int root) { return !suspended && !disabled.contains(root); }
    }
    private final IdentityHashMap<T,Node<T>> nodes=new IdentityHashMap<>();
    private final Map<PowerPoint,Node<T>> positions=new HashMap<>();
    private Node<T> first;
    private final ArrayDeque<Node<T>> detachedToClean=new ArrayDeque<>();
    private Snapshot<T> snapshot=new Snapshot<>();
    private Builder builder;
    private int radius,lastWork;
    private long revision,published,rebuilds,restarts,candidateChecks,scanSerial;

    public PowerTopology(int radius) { setRadius(radius);published=revision; }
    public void setRadius(int value) {
        if(value<1 || value>16)throw new IllegalArgumentException("Power radius must be 1..16");
        if(radius==value)return;
        radius=value;revision++;snapshot.suspended=true;
    }
    public Node<T> add(T value,PowerPoint point,Kind kind,Receiver receiver) {
        Objects.requireNonNull(value);Objects.requireNonNull(point);Objects.requireNonNull(kind);
        if(kind==Kind.CONSUMER)Objects.requireNonNull(receiver);
        var existing=nodes.get(value);if(existing!=null)return existing;
        var occupied=positions.get(point);if(occupied!=null)remove(occupied.value);
        var n=new Node<>(value,point,kind,receiver);n.next=first;if(first!=null)first.previous=n;first=n;
        nodes.put(value,n);positions.put(point,n);revision++;return n;
    }
    public Node<T> find(T value) { return nodes.get(value); }
    public void remove(T value) {
        var n=nodes.remove(value);if(n==null)return;
        n.alive=false;positions.remove(n.point,n);
        if(n.previous==null)first=n.next;else n.previous.next=n.next;
        if(n.next!=null)n.next.previous=n.previous;
        Integer root=snapshot.poleGroups.get(n);if(root!=null)snapshot.disabled.add(root);
        if(builder!=null && n.kind==Kind.POLE)builder.removedPoles.add(n);
        // Preserve a live capture cursor, then release its detached links using the same work budget.
        if(builder!=null && builder.phase==0)detachedToClean.addLast(n);else n.next=null;
        n.previous=null;n.route=null;revision++;
    }
    /** Logical work budget, including empty bucket probes; no unbounded all-pairs loop is hidden here. */
    public int advance(int budget) {
        if(budget<0)throw new IllegalArgumentException("Negative work budget");
        lastWork=0;
        if(published==revision)return 0;
        if(builder==null || builder.buildRadius!=radius) {
            if(builder!=null)restarts++;
            builder=new Builder(revision);
        }
        while(lastWork<budget && !builder.done) { builder.step();lastWork++; }
        if(builder.done) { snapshot=builder.result;published=builder.version;builder=null;rebuilds++; }
        return lastWork;
    }
    public boolean pending() { return published!=revision; }
    public int size() { return nodes.size(); }
    public int lastWork() { return lastWork; }
    public long rebuilds() { return rebuilds; }
    public long restarts() { return restarts; }
    public long candidateChecks() { return candidateChecks; }
    public int networks() { return snapshot.suspended ? 0:snapshot.networks-snapshot.disabled.size(); }

    public boolean connected(Node<T> a,Node<T> b) {
        if(a==null || b==null || !a.alive || !b.alive)return false;
        for(int root:groups(a))if(snapshot.valid(root))for(int other:groups(b))if(root==other)return true;
        return false;
    }
    private int[] groups(Node<T> n) { return snapshot.memberships.getOrDefault(n,EMPTY); }
    private static final int[] EMPTY=new int[0];
    /** Capture at most scanBudget candidates. Repeated memberships never duplicate a receiver. */
    public boolean prepareDemand(Node<T> source,int scanBudget) {
        if(scanBudget<1 || scanBudget>1024)throw new IllegalArgumentException("Invalid recipient budget");
        if(source==null || !source.alive || source.kind!=Kind.SOURCE)return false;
        if(source.route==null || source.route.generation!=rebuilds)source.route=new Route<>(snapshot,groups(source),rebuilds);
        var r=source.route;r.batch.clear();
        if(r.groups.length==0)return false;
        long serial=++scanSerial;
        for(int i=0,limit=Math.min(scanBudget,r.entries);i<limit;i++) {
            int group=r.group;r.group=(r.group+1)%r.groups.length;
            int root=r.groups[group];if(!snapshot.valid(root))continue;
            var targets=snapshot.consumers.getOrDefault(root,List.of());if(targets.isEmpty())continue;
            int index=r.cursors[group]%targets.size();r.cursors[group]=(index+1)%targets.size();
            var candidate=targets.get(index);
            if(!candidate.alive || candidate.visited==serial)continue;
            candidate.visited=serial;
            if(candidate.receiver.receive(1,true)>0) {
                int indexInBatch=r.batch.size();
                if(indexInBatch==r.batchGroups.length)r.batchGroups=Arrays.copyOf(r.batchGroups,indexInBatch*2);
                r.batchGroups[indexInBatch]=root;r.batch.add(candidate);
            }
        }
        return !r.batch.isEmpty();
    }
    /** Returns the energy actually accepted. The caller burns fuel only for a successful supply tick. */
    public int distributePrepared(Node<T> source,int available) {
        if(source==null || !source.alive || source.route==null || source.route.generation!=rebuilds || available<=0)return 0;
        var r=source.route;int left=available;
        for(int i=0;i<r.batch.size() && left>0;i++) {
            var recipient=r.batch.get(i);
            if(!recipient.alive || !snapshot.valid(r.batchGroups[i]))continue;
            int share=Math.max(1,left/(r.batch.size()-i));
            int accepted=Math.clamp(recipient.receiver.receive(share,false),0,share);left-=accepted;
        }
        r.batch.clear();return available-left;
    }

    private final class Pole {
        final Node<T> node;
        final int index;
        Pole parent=this;
        int rank;
        Pole(Node<T> node,int index) { this.node=node;this.index=index; }
        Pole root() { Pole p=this;while(p.parent!=p){p.parent=p.parent.parent;p=p.parent;}return p; }
        void join(Pole other) {
            var a=root();var b=other.root();if(a==b)return;
            if(a.rank<b.rank)a.parent=b;else { b.parent=a;if(a.rank==b.rank)a.rank++; }
        }
    }
    private final class Builder {
        final long version;
        final int buildRadius=radius;
        final ArrayList<Node<T>> removedPoles=new ArrayList<>();
        final Snapshot<T> result=new Snapshot<>();
        final SpatialIndex<Pole> spatial=new SpatialIndex<>(radius);
        final ArrayList<Pole> poles=new ArrayList<>();
        final ArrayList<Node<T>> devices=new ArrayList<>();
        final Set<Integer> roots=new HashSet<>(),deviceGroups=new HashSet<>();
        Node<T> cursor=first;
        SpatialIndex<Pole>.Cursor query;
        int phase,index;
        boolean done;
        Builder(long version) { this.version=version; }
        void step() {
            switch(phase) {
                case 0 -> capture();
                case 1 -> connect();
                case 2 -> label();
                case 3 -> attach();
                case 4 -> quarantine();
                default -> {
                    // Removal can arrive between the last quarantine step and this publication.
                    if(index<removedPoles.size())phase=4;
                    else { result.networks=roots.size();done=true; }
                }
            }
        }
        private void capture() {
            if(cursor==null) {
                if(!detachedToClean.isEmpty()) { detachedToClean.removeFirst().next=null;return; }
                phase=1;index=0;return;
            }
            var n=cursor;cursor=cursor.next;if(!n.alive)return;
            if(n.kind==Kind.POLE) { var p=new Pole(n,poles.size());poles.add(p);spatial.add(n.point,p); }
            else devices.add(n);
        }
        private void connect() {
            if(index==poles.size()) { phase=2;index=0;return; }
            var current=poles.get(index);
            if(query==null) { query=spatial.query(current.node.point);return; }
            if(query.done()) { query=null;index++;return; }
            var candidate=query.step();
            if(candidate!=null) {
                candidateChecks++;
                if(candidate.index<current.index && current.node.point.within(candidate.node.point,buildRadius))current.join(candidate);
            }
        }
        private void label() {
            if(index==poles.size()) { phase=3;index=0;return; }
            var p=poles.get(index++);int root=p.root().index;result.poleGroups.put(p.node,root);roots.add(root);
        }
        private void quarantine() {
            if(index==removedPoles.size()) { phase=5;return; }
            Integer root=result.poleGroups.get(removedPoles.get(index++));
            if(root!=null)result.disabled.add(root);
        }
        private void attach() {
            if(index==devices.size()) { phase=4;index=0;return; }
            var device=devices.get(index);
            if(query==null) { deviceGroups.clear();query=spatial.query(device.point);return; }
            if(!query.done()) {
                var p=query.step();
                if(p!=null) { candidateChecks++;if(device.point.within(p.node.point,buildRadius))deviceGroups.add(p.root().index); }
                return;
            }
            int[] membership=new int[deviceGroups.size()];int i=0;
            for(int root:deviceGroups) {
                membership[i++]=root;
                if(device.kind==Kind.CONSUMER)result.consumers.computeIfAbsent(root,k->new ArrayList<>()).add(device);
            }
            Arrays.sort(membership);result.memberships.put(device,membership);query=null;index++;
        }
    }
}
