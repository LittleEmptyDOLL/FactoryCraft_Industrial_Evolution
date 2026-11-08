package factorycraft.research;

import factorycraft.simulation.research.ResearchLedger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Single overworld SavedData shared across dimensions; no static per-server progress survives unload. */
public final class ResearchData extends SavedData {
    private final Map<String,ResearchLedger> teams=new HashMap<>();
    public static ResearchData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ResearchData::new,ResearchData::load),"factorycraft_research"); }
    public ResearchLedger team(String key) { return teams.computeIfAbsent(key,k->new ResearchLedger()); }
    public static ResearchData load(CompoundTag tag,HolderLookup.Provider r) {
        var data=new ResearchData();var list=tag.getList("Teams",10);
        for(int i=0;i<list.size();i++){var t=list.getCompound(i);var levels=new HashMap<String,Integer>();var progress=new HashMap<String,ResearchLedger.Progress>();var lv=t.getCompound("Levels");for(var id:lv.getAllKeys())levels.put(id,lv.getInt(id));var p=t.getCompound("Progress");for(var id:p.getAllKeys()){var n=p.getCompound(id);progress.put(id,new ResearchLedger.Progress(n.getString("Signature"),n.getInt("Units")));}var ledger=new ResearchLedger();ledger.restore(t.getString("Selected"),levels,progress);data.teams.put(t.getString("Team"),ledger);}return data;
    }
    public CompoundTag save(CompoundTag tag,HolderLookup.Provider r) {
        tag.putInt("Schema",1);var list=new ListTag();for(var entry:teams.entrySet()){var t=new CompoundTag();t.putString("Team",entry.getKey());var ledger=entry.getValue();t.putString("Selected",ledger.selected());var levels=new CompoundTag();ledger.levels().forEach(levels::putInt);t.put("Levels",levels);var progress=new CompoundTag();ledger.progress().forEach((id,p)->{var n=new CompoundTag();n.putString("Signature",p.signature());n.putInt("Units",p.units());progress.put(id,n);});t.put("Progress",progress);list.add(t);}tag.put("Teams",list);return tag;
    }
}
