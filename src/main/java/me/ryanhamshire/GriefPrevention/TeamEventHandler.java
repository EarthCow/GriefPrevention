package me.ryanhamshire.GriefPrevention;

import com.booksaw.betterTeams.customEvents.DisbandTeamEvent;
import com.booksaw.betterTeams.customEvents.PlayerJoinTeamEvent;
import com.booksaw.betterTeams.customEvents.PlayerLeaveTeamEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TeamEventHandler implements Listener
{
    @EventHandler
    public void onPlayerJoinTeam(PlayerJoinTeamEvent event) {
        TeamData teamData = GriefPrevention.instance.dataStore.getTeamData(event.getTeam().getID());
        for (Claim claim : teamData.getClaims()) {
            claim.setPermission(event.getPlayer().getUniqueId().toString(), ClaimPermission.Build);
        }
    }

    @EventHandler
    public void onPlayerLeaveTeam(PlayerLeaveTeamEvent event) {
        TeamData teamData = GriefPrevention.instance.dataStore.getTeamData(event.getTeam().getID());
        for (Claim claim : teamData.getClaims()) {
            claim.setPermission(event.getPlayer().getUniqueId().toString(), null);
        }
    }

    @EventHandler
    public void onDisbandTeam(DisbandTeamEvent event) {
        GriefPrevention.instance.getLogger().warning("disband team ran");
        GriefPrevention.instance.dataStore.getTeamData(event.getTeam().getID()).delete();
    }
}
