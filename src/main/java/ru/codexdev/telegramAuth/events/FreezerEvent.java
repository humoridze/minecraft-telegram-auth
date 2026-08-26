/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
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
