/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Handler {
    public static void kick(String name, String reason) {
        TelegramAuth plugin = TelegramAuth.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(name);
            if (player != null) {
                player.kickPlayer(reason);
            }
        });
    }

    public static void sendMCmessage(String name, String message) {
        TelegramAuth plugin = TelegramAuth.getInstance();
        if (plugin == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(name);
            if (player != null) {
                player.chat(message);
            }
        });
    }
}
