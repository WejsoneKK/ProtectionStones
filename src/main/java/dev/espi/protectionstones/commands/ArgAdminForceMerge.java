/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dev.espi.protectionstones.commands;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.FlagHandler;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.WGMerge;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.*;

public class ArgAdminForceMerge {

    private static Map<Flag<?>, Object> getFlags(Map<Flag<?>, Object> flags) {
        Map<Flag<?>, Object> f = new HashMap<>(flags);
        f.remove(FlagHandler.PS_BLOCK_MATERIAL);
        f.remove(FlagHandler.PS_MERGED_REGIONS_TYPES);
        f.remove(FlagHandler.PS_MERGED_REGIONS);
        f.remove(FlagHandler.PS_NAME);
        f.remove(FlagHandler.PS_HOME);

        return f;
    }

    private static boolean areDomainsEqual(DefaultDomain o1, DefaultDomain o2) {
        for (UUID uuid : o1.getUniqueIds()) {
            if (!o2.contains(uuid)) return false;
        }
        for (UUID uuid : o2.getUniqueIds()) {
            if (!o1.contains(uuid)) return true;
        }
        return true;
    }

    // /ps admin forcemerge [world]
    public static boolean argumentAdminForceMerge(CommandSender p, String[] args) {
        if (args.length < 3) {
            PSL.msg(p, PSL.ADMIN_FORCEMERGE_HELP.msg());
            return true;
        }

        String world = args[2];
        World w = Bukkit.getWorld(world);

        if (w == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return true;
        }

        RegionManager rm = WGUtils.getRegionManagerWithWorld(Bukkit.getWorld(world));


        
        Queue<String> toVisitIDs = new LinkedList<>();
        Set<String> visitedIDs = new HashSet<>();

        // add regions to check
        for (ProtectedRegion r : rm.getRegions().values()) toVisitIDs.add(r.getId());

        // go through all regions
        while (!toVisitIDs.isEmpty()) {
            ProtectedRegion pr = rm.getRegion(toVisitIDs.poll()); // get top region and remove from queue
            if (pr == null) continue;
            if (pr.getParent() != null) continue;
            if (!ProtectionStones.isPSRegion(pr)) continue;
            if (visitedIDs.contains(pr.getId())) continue; // if already visited, skip

            PSRegion prr = PSRegion.fromWGRegion(w, pr);
            Map<Flag<?>, Object> baseFlags = getFlags(pr.getFlags()); // comparison flags

            Set<String> traversed = new HashSet<>();

            boolean didMerge = true;
            // keep merging until there are no regions left to merge in
            while (didMerge) {
                didMerge = false;

                for (ProtectedRegion toMerge : rm.getApplicableRegions(pr)) {
                    if (traversed.contains(toMerge.getId())) continue; // if already traversed

                    traversed.add(toMerge.getId()); // this region has been visited
                    if (toMerge.getId().equals(pr.getId())) continue;
                    if (!ProtectionStones.isPSRegion(toMerge)) continue;

                    PSRegion toMergeR = PSRegion.fromWGRegion(w, toMerge);
                    Map<Flag<?>, Object> mergeFlags = getFlags(toMerge.getFlags()); // comparison flags

                    if (areDomainsEqual(toMerge.getOwners(), pr.getOwners()) && areDomainsEqual(toMerge.getMembers(), pr.getMembers()) && toMerge.getParent() == null && baseFlags.equals(mergeFlags)) {
                        didMerge = true;
                        visitedIDs.add(toMerge.getId());

                        try {
                            p.sendMessage(ChatColor.GRAY + "Merging " + prr.getID() + " and " + toMergeR.getID() + "...");
                            WGMerge.mergeRegions(w, rm, prr, Arrays.asList(prr, toMergeR));
                        } catch (WGMerge.RegionHoleException ignored) {
                            // TODO
                        }

                        break; // leave when a region is merged
                    }
                }
            }
        }

        p.sendMessage("Done!");

        return true;
    }
}
