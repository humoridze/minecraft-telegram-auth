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
