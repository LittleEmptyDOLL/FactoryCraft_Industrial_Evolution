package factorycraft.simulation.research;

import java.util.*;

/** Server-independent team ledger. The caller defers inventory observers until this commit returns. */
public final class ResearchLedger {
    public record Technology(String id,List<String> prerequisites,int units,int ticks,boolean repeatable,String signature) {
        public Technology { prerequisites=List.copyOf(prerequisites);if(id.isEmpty() || units<1 || units>100000 || ticks<1 || ticks>72000)throw new IllegalArgumentException("Invalid technology"); }
        public int cost(int level) { return (int)Math.min(1000000L,(long)units*(repeatable ? level+1L:1)); }
    }
    public record Progress(String signature,int units) { public Progress { if(units<0 || units>1000000)throw new IllegalArgumentException("Invalid research progress"); } }
    private final Map<String,Integer> levels=new HashMap<>();
    private final Map<String,Progress> progress=new HashMap<>();
    private String selected="";
    public String selected() { return selected; }
    public int level(String id) { return levels.getOrDefault(id,0); }
    public Map<String,Integer> levels() { return Map.copyOf(levels); }
    public Map<String,Progress> progress() { return Map.copyOf(progress); }
    public int units(Technology tech) { var p=progress.get(tech.id());return p!=null && p.signature.equals(tech.signature()) ? p.units:0; }
    public boolean available(Technology tech) { return level(tech.id)<1000000 && (tech.repeatable || level(tech.id)==0) && tech.prerequisites.stream().allMatch(id->level(id)>0); }
    public boolean select(Technology tech) { if(!available(tech))return false;selected=tech.id;return true; }
    /** No caller can consume packs for a stale selection or the already finished last unit. */
    public boolean completeUnit(Technology tech,Runnable consumeWithoutObservers) {
        if(!selected.equals(tech.id) || !available(tech))return false;
        int n=units(tech)+1;consumeWithoutObservers.run();
        if(n>=tech.cost(level(tech.id))) { levels.put(tech.id,level(tech.id)+1);progress.remove(tech.id);if(!tech.repeatable)selected=""; }
        else progress.put(tech.id,new Progress(tech.signature,n));
        return true;
    }
    public void restore(String selected,Map<String,Integer> levels,Map<String,Progress> progress) {
        if(levels.values().stream().anyMatch(n->n<1 || n>1000000))throw new IllegalArgumentException("Invalid research level");
        this.selected=Objects.requireNonNull(selected);this.levels.clear();this.levels.putAll(levels);this.progress.clear();this.progress.putAll(progress);
    }
}
