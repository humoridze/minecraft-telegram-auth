/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth.events;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.humoridze.telegramAuth.AuthManager;
import ru.humoridze.telegramAuth.TelegramAuth;

public class PlayerLoginEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
        if (event.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        String username = event.getPlayer().getName();
        TelegramAuth plugin = TelegramAuth.getInstance();
        String telegramLink = plugin != null ? plugin.getTelegramLink() : "changeme";

        if (!AuthManager.isUserRegistered(username)) {
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Вы не зарегистрированы!\n" +
                            ChatColor.YELLOW + "Зарегистрируйтесь через Telegram бота.\n" +
                            ChatColor.AQUA + telegramLink);
            return;
        }

        if (!AuthManager.isUserWhitelisted(username)) {
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_WHITELIST,
                    ChatColor.RED + "Вы не в вайтлисте!\n" +
                            ChatColor.YELLOW + "Обратитесь к администратору для добавления в вайтлист.");
        }
    }
}
