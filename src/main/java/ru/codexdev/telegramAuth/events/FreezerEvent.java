/*
 * Copyright (C) 2024 humoridze
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.codexdev.telegramAuth.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.concurrent.ConcurrentHashMap;

public class FreezerEvent implements Listener {

    private static final ConcurrentHashMap<String, Location> freezeplayer = new ConcurrentHashMap<>();

    public static void freezeplayer(String name) {
        Player player = org.bukkit.Bukkit.getPlayer(name);
        if (player != null) {
            freezeplayer.put(name.toLowerCase(), player.getLocation().clone());
        }
    }

    public static void unfreezeplayer(String name) {
        freezeplayer.remove(name.toLowerCase());
    }

    public static boolean isPlayerFrozen(String name) {
        return freezeplayer.containsKey(name.toLowerCase());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location freezeLocation = freezeplayer.get(event.getPlayer().getName().toLowerCase());
        if (freezeLocation == null) {
            return;
        }
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        if (destination.getX() == freezeLocation.getX()
                && destination.getY() == freezeLocation.getY()
                && destination.getZ() == freezeLocation.getZ()) {
            return;
        }
        Location locked = freezeLocation.clone();
        locked.setYaw(destination.getYaw());
        locked.setPitch(destination.getPitch());
        event.setTo(locked);
    }
}
