/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth.events;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.humoridze.telegramAuth.AuthManager;
import ru.humoridze.telegramAuth.events.FreezerEvent;
import ru.humoridze.telegramAuth.events.MuterEvent;

public class OnJoinEvent implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        String username = p.getName();

        if (AuthManager.isUserRegistered(username) &&
                AuthManager.isUserWhitelisted(username)) {

            // Проверяем совпадение IP
            String currentIp = p.getAddress().getAddress().getHostAddress();
            String lastIp = AuthManager.getLastIp(username);

            if (lastIp != null && currentIp.equals(lastIp)) {
                // IP совпадает - пропускаем ввод пароля, только подтверждение через TG
                p.sendMessage(ChatColor.GREEN + "IP адрес совпадает с последним входом.");
                p.sendMessage(ChatColor.YELLOW + "Ожидается подтверждение через Telegram...");

                // Замораживаем игрока до подтверждения через TG
                FreezerEvent.freezeplayer(username);
                MuterEvent.mute(username, ChatColor.YELLOW + "Ожидается подтверждение через Telegram");

                // Отправляем запрос на подтверждение в Telegram
                AuthManager.handleSuccessfulAuth(p);
            } else {
                // IP изменился или отсутствует - требуем полную авторизацию
                if (lastIp == null) {
                    p.sendMessage(ChatColor.YELLOW + "Первый вход с этого IP адреса.");
                } else {
                    p.sendMessage(ChatColor.YELLOW + "IP адрес изменился. Требуется полная авторизация.");
                }

                // Стандартная процедура с вводом пароля
                AuthManager.handlePlayerJoin(p);
            }
        } else {
            // Если не зарегистрирован или не в вайтлисте - обрабатываем как обычно
            AuthManager.handlePlayerJoin(p);
        }
    }
}