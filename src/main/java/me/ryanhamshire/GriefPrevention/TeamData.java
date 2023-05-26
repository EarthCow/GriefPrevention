/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.booksaw.betterTeams.Team;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ScheduledFuture;

//holds all of GriefPrevention's player-tied data
public class TeamData
{
    //the team's ID
    public UUID teamID;

    //the player's claims
    private Vector<Claim> claims = null;

    //how many claim blocks the player has earned via play time
    private Integer baseClaimBlocks = null;

    //temporary holding area to avoid opening data files too early
    private int newlyDepositedClaimBlocks = 0;

    private HashMap<UUID, String> withdrawRequests = new HashMap<>();

    private HashMap<UUID, ScheduledFuture<?>> withdrawRequestTimeouts = new HashMap<>();

    public HashMap<UUID, String> getWithdrawRequests()
    {
        return withdrawRequests;
    }

    public HashMap<UUID, ScheduledFuture<?>> getWithdrawRequestTimeouts() {
        return withdrawRequestTimeouts;
    }

    public void setWithdrawRequests(HashMap<UUID, String> withdrawRequests)
    {
        this.withdrawRequests = withdrawRequests;
    }

    public void setWithdrawRequestTimeouts(HashMap<UUID, ScheduledFuture<?>> withdrawRequestTimeouts) {
        this.withdrawRequestTimeouts = withdrawRequestTimeouts;
    }

    //the number of claim blocks a team has available for claiming land
    public int getRemainingClaimBlocks()
    {
        int remainingBlocks = this.getBaseClaimBlocks() + GriefPrevention.instance.dataStore.getGroupBonusBlocks(this.teamID);
        for (int i = 0; i < this.getClaims().size(); i++)
        {
            Claim claim = this.getClaims().get(i);
            remainingBlocks -= claim.getArea();
        }

        return remainingBlocks;
    }

    //don't load data from secondary storage until it's needed
    public synchronized int getBaseClaimBlocks()
    {
        if (this.baseClaimBlocks == null) this.loadDataFromSecondaryStorage();

        //update claim blocks with any he has accrued during his current play session
        if (this.newlyDepositedClaimBlocks > 0)
        {
            //move any in the holding area
            this.baseClaimBlocks = this.baseClaimBlocks + this.newlyDepositedClaimBlocks;

            this.newlyDepositedClaimBlocks = 0;
            return this.baseClaimBlocks;
        }

        return baseClaimBlocks;
    }

    public void setBaseClaimBlocks(Integer baseClaimBlocks)
    {
        this.baseClaimBlocks = baseClaimBlocks;
        this.newlyDepositedClaimBlocks = 0;
    }

    private void loadDataFromSecondaryStorage()
    {
        //reach out to secondary storage to get any data there
        TeamData storageData = GriefPrevention.instance.dataStore.getTeamDataFromStorage(this.teamID);

        if (this.baseClaimBlocks == null)
        {
            if (storageData.baseClaimBlocks != null)
            {
                this.baseClaimBlocks = storageData.baseClaimBlocks;

                //ensure at least minimum accrued are accrued (in case of settings changes to increase initial amount)
                if (GriefPrevention.instance.config_advanced_fixNegativeClaimblockAmounts && (this.baseClaimBlocks < GriefPrevention.instance.config_claims_initialBlocks))
                {
                    this.baseClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
                }

            }
            else
            {
                this.baseClaimBlocks = GriefPrevention.instance.config_claims_initialBlocks;
            }
        }
    }

    public Vector<Claim> getClaims()
    {
        if (this.claims == null)
        {
            this.claims = new Vector<>();

            //find all the claims belonging to this player and note them for future reference
            DataStore dataStore = GriefPrevention.instance.dataStore;
            int totalClaimsArea = 0;
            for (int i = 0; i < dataStore.claims.size(); i++)
            {
                Claim claim = dataStore.claims.get(i);
                if (!claim.inDataStore)
                {
                    dataStore.claims.remove(i--);
                    continue;
                }
                if (teamID.equals(claim.ownerID))
                {
                    this.claims.add(claim);
                    totalClaimsArea += claim.getArea();
                }
            }

            //ensure player has claim blocks for his claims, and at least the minimum accrued
            this.loadDataFromSecondaryStorage();

            //if total claimed area is more than total blocks available
            int totalBlocks = this.baseClaimBlocks;
            if (GriefPrevention.instance.config_advanced_fixNegativeClaimblockAmounts && totalBlocks < totalClaimsArea)
            {
                Team team = Team.getTeam(teamID);
                GriefPrevention.AddLogEntry(team.getName() + " has more claimed land than blocks available.  Adding blocks to fix.", CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry(team.getName() + " Accrued blocks: " + this.getBaseClaimBlocks(), CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
                for (Claim claim : this.claims)
                {
                    if (!claim.inDataStore) continue;
                    GriefPrevention.AddLogEntry(
                            GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " // "
                                    + GriefPrevention.getfriendlyLocationString(claim.getGreaterBoundaryCorner()) + " = "
                                    + claim.getArea()
                            , CustomLogEntryTypes.Debug, true);
                }

                //try to fix it by adding to accrued blocks
                this.baseClaimBlocks = totalClaimsArea; //Set accrued blocks to equal total claims

                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = this.baseClaimBlocks;
                GriefPrevention.AddLogEntry("New total blocks: " + totalBlocks, CustomLogEntryTypes.Debug, true);

                //if that didn't fix it, then make up the difference with bonus blocks
                GriefPrevention.AddLogEntry(team.getName() + " Accrued blocks: " + this.getBaseClaimBlocks(), CustomLogEntryTypes.Debug, true);
                //Recalculate total blocks (accrued + bonus + permission group bonus)
                totalBlocks = this.baseClaimBlocks;
                GriefPrevention.AddLogEntry("Total blocks: " + totalBlocks + " Total claimed area: " + totalClaimsArea, CustomLogEntryTypes.Debug, true);
                GriefPrevention.AddLogEntry("Remaining claim blocks to use: " + this.getRemainingClaimBlocks() + " (should be 0)", CustomLogEntryTypes.Debug, true);
            }
        }

        for (int i = 0; i < this.claims.size(); i++)
        {
            if (!claims.get(i).inDataStore)
            {
                claims.remove(i--);
            }
        }

        return claims;
    }

    public void delete() {
        Bukkit.getScheduler().runTask(GriefPrevention.instance,() -> {
            GriefPrevention.instance.dataStore.deleteClaimsForTeam(this.teamID, true);
            GriefPrevention.instance.dataStore.clearCachedTeamData(this.teamID);
            GriefPrevention.instance.dataStore.removeTeamDataFromStorage(this.teamID);
        });
    }

    public void depositClaimBlocks(int howMany)
    {
        this.newlyDepositedClaimBlocks += howMany;
    }

}