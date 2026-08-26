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

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.codexdev.telegramAuth.AuthManager;
import ru.codexdev.telegramAuth.TelegramAuth;

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
