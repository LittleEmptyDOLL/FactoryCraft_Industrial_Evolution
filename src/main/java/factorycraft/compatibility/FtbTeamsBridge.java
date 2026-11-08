package factorycraft.compatibility;

import java.lang.reflect.Method;
import java.util.*;

/** Optional linkage only. Verified against FTB Teams' public 1.21.1 API, never client manager internals. */
public final class FtbTeamsBridge {
    private static Method api,loaded,manager,forPlayer,byId,teamId,shortName;
    private static boolean failed;
    private static Object manager() throws ReflectiveOperationException {
        if(failed)throw new IllegalStateException("Unsupported FTB Teams API");
        if(api==null) {
            api=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api");var a=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            loaded=a.getMethod("isManagerLoaded");manager=a.getMethod("getManager");var m=Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            forPlayer=m.getMethod("getTeamForPlayerID",UUID.class);byId=m.getMethod("getTeamByID",UUID.class);teamId=Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getTeamId");shortName=Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getShortName");
        }
        var instance=api.invoke(null);return instance!=null && Boolean.TRUE.equals(loaded.invoke(instance)) ? manager.invoke(instance):null;
    }
    public static Optional<UUID> team(UUID player) {
        try { var m=manager();if(m==null)return Optional.empty();var t=(Optional<?>)forPlayer.invoke(m,player);return t.isEmpty() ? Optional.empty():Optional.of((UUID)teamId.invoke(t.get())); }
        catch(ReflectiveOperationException | RuntimeException e){fail(e);return Optional.empty();}
    }
    public static boolean exists(UUID id) {
        try { var m=manager();return m!=null && ((Optional<?>)byId.invoke(m,id)).isPresent(); }
        catch(ReflectiveOperationException | RuntimeException e){fail(e);return false;}
    }
    public static String label(UUID id) {
        try {var m=manager();if(m==null)return "FTB Teams";var t=(Optional<?>)byId.invoke(m,id);return t.isEmpty() ? "FTB Teams":"FTB Teams: "+shortName.invoke(t.get());}catch(ReflectiveOperationException | RuntimeException e){return "FTB Teams";}
    }
    private static void fail(Exception e) { if(!failed)com.mojang.logging.LogUtils.getLogger().error("FactoryCraft FTB Teams integration unavailable; research is paused, not redirected",e);failed=true; }
    private FtbTeamsBridge() {}
}
