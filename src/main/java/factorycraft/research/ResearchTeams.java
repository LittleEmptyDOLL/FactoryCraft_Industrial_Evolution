package factorycraft.research;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import java.util.*;

public final class ResearchTeams {
    public static Optional<String> resolve(Player player) {
        if(player.level().isClientSide)return Optional.empty();
        if(ModList.get().isLoaded("ftbteams"))return factorycraft.compatibility.FtbTeamsBridge.team(player.getUUID()).map(id->"ftb:"+id);
        return Optional.of(player.getTeam()!=null ? "vanilla:"+player.getTeam().getName():"player:"+player.getUUID());
    }
    public static boolean exists(MinecraftServer server,String scope) {
        try {
            if(scope.startsWith("ftb:"))return ModList.get().isLoaded("ftbteams") && factorycraft.compatibility.FtbTeamsBridge.exists(UUID.fromString(scope.substring(4)));
            if(scope.startsWith("vanilla:"))return !ModList.get().isLoaded("ftbteams") && server.getScoreboard().getPlayerTeam(scope.substring(8))!=null;
            return scope.startsWith("player:") && !ModList.get().isLoaded("ftbteams") && UUID.fromString(scope.substring(7))!=null;
        }catch(IllegalArgumentException e){return false;}
    }
    public static boolean member(Player player,String scope) { return resolve(player).filter(scope::equals).isPresent(); }
    private ResearchTeams() {}
}
