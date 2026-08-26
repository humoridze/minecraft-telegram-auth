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

package ru.codexdev.telegramAuth;

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
