/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.codexdev.telegramAuth.events;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.concurrent.ConcurrentHashMap;

public class MuterEvent implements Listener {

    private static final ConcurrentHashMap<String, String> mutedPlayers = new ConcurrentHashMap<>();

    public static void mute(String name, String reason) {
        mutedPlayers.put(name.toLowerCase(), reason);
    }

    public static void unmute(String name) {
        mutedPlayers.remove(name.toLowerCase());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String muteReason = mutedPlayers.get(event.getPlayer().getName().toLowerCase());
        if (muteReason != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(muteReason);
        }
    }
}
