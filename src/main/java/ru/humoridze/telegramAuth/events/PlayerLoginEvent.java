/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth.events;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.humoridze.telegramAuth.AuthManager;

public class PlayerLoginEvent implements Listener {
    
    @EventHandler
    public void onPlayerLogin(org.bukkit.event.player.PlayerLoginEvent event) {
        String username = event.getPlayer().getName();
        
        // Проверяем, зарегистрирован ли пользователь
        if (!AuthManager.isUserRegistered(username)) {
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_OTHER,
                ChatColor.RED + "Вы не зарегистрированы!\n" +
                ChatColor.YELLOW + "Зарегистрируйтесь через Telegram бота.\n" + ChatColor.AQUA + "https://t.me/stonegladebot");
            return;
        }

        // Проверяем, в вайтлисте ли пользователь
        if (!AuthManager.isUserWhitelisted(username)) {
            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_WHITELIST,
                ChatColor.RED + "Вы не в вайтлисте!\n" +
                ChatColor.YELLOW + "Обратитесь к администратору для добавления в вайтлист.");
            return;
        }
        
        // Если все проверки пройдены, разрешаем вход
        event.allow();
    }
} 