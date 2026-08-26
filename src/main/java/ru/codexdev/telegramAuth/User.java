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
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class User {
    public Long chatid = null;
    public String username = null;
    public String firstname = null;
    public String lastname = null;
    public boolean active = false;
    public boolean twofactor = false;
    public Player player = null;
    public String playername = "";

    public User() {
        // Конструктор по умолчанию для создания пользователей из новой системы
    }

    public static User getUser(String playername) {
        if (playername == null || playername.isEmpty()) {
            return null;
        }
        // Сначала ищем в новой системе AuthManager
        if (AuthManager.isUserRegistered(playername)) {
            Long chatId = AuthManager.getTelegramChatId(playername);
            if (chatId != null) {
                User user = new User();
                user.playername = playername;
                user.chatid = chatId;
                user.active = true;
                user.player = Bukkit.getPlayer(playername);
                return user;
            }
        }
        return null;
    }

    public void kick() {
        if (this.player != null) {
            Handler.kick(this.player.getName(), "Отключен");
        }
    }

    public static List<String> getPlayerNames(Long chatid) {
        List<String> names = new ArrayList<>();
        for (User user : getUserList()) {
            if (user != null && user.chatid != null && user.chatid.equals(chatid)) {
                names.add(user.playername);
            }
        }
        return names;
    }

    public static User getOnlineUser(Long chatid) {
        if (chatid == null) {
            return null;
        }
        if (BotTelegram.curentplayer.containsKey(chatid.toString())) {
            String playername = BotTelegram.curentplayer.get(chatid.toString());
            return getUser(playername);
        }
        List<String> players = getPlayerNames(chatid);
        for (User user : getUserList()) {
            if (user.player != null && players.contains(user.player.getName())) {
                BotTelegram.curentplayer.put(chatid.toString(), user.playername);
                return user;
            }
        }
        return null;
    }

    public static User getCurrentUser(Long chatid) {
        if (chatid == null) {
            return null;
        }
        if (BotTelegram.curentplayer.containsKey(chatid.toString())) {
            return getUser(BotTelegram.curentplayer.get(chatid.toString()));
        } else {
            for (User user : getUserList()) {
                if (user.chatid != null && user.chatid.equals(chatid)) {
                    BotTelegram.curentplayer.put(chatid.toString(), user.playername);
                    return user;
                }
            }
            return null;
        }
    }

    // Кэш для списка пользователей
    private static List<User> userListCache = null;
    private static long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 30000; // 30 секунд
    
    public static List<User> getUserList() {
        List<User> users = new ArrayList<>();
        List<String> registeredUsers = AuthManager.getRegisteredUsers();
        for (String username : registeredUsers) {
            Long chatId = AuthManager.getTelegramChatId(username);
            if (chatId != null) {
                User user = new User();
                user.playername = username;
                user.chatid = chatId;
                user.active = true;
                user.player = Bukkit.getPlayer(username);
                users.add(user);
            }
        }
        return users;
    }
}