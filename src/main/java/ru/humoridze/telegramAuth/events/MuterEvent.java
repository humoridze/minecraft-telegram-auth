/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth.events;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;

public class MuterEvent implements Listener {

    private static Map<String, String> mutedPlayers = new HashMap<String, String>();
    
    public static void mute(String name, String reason) {
        mutedPlayers.put(name, reason);
    }
    
    public static void unmute(String name) {
        mutedPlayers.remove(name);
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String playerName = event.getPlayer().getName();
        if (mutedPlayers.containsKey(playerName)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(mutedPlayers.get(playerName));
        }
    }
} 