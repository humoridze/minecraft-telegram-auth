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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.codexdev.telegramAuth.AuthManager;
import ru.codexdev.telegramAuth.TelegramAuth;

public class OnJoinEvent implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        if (AuthManager.isUserRegistered(username) && AuthManager.isUserWhitelisted(username)) {
            String currentIp = player.getAddress() != null
                    ? player.getAddress().getAddress().getHostAddress()
                    : null;
            String lastIp = AuthManager.getLastIp(username);
            TelegramAuth plugin = TelegramAuth.getInstance();
            boolean skipPassword = plugin != null && plugin.skipPasswordOnSameIp();

            if (skipPassword && lastIp != null && lastIp.equals(currentIp)) {
                AuthManager.beginAuthSession(
                        player,
                        ChatColor.YELLOW + "Ожидается подтверждение через Telegram",
                        ChatColor.YELLOW + "Подтвердите вход",
                        "через Telegram"
                );
                player.sendMessage(ChatColor.GREEN + "IP адрес совпадает с последним входом.");
                AuthManager.handleSuccessfulAuth(player);
            } else {
                if (lastIp == null) {
                    player.sendMessage(ChatColor.YELLOW + "Первый вход с этого IP адреса.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "IP адрес изменился. Требуется полная авторизация.");
                }
                AuthManager.handlePlayerJoin(player);
            }
        } else {
            AuthManager.handlePlayerJoin(player);
        }
    }
}
