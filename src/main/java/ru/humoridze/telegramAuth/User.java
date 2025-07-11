/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

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
import java.util.UUID;

public class User {
    public Long chatid = null;
    public String username = null;
    public String firstname = null;
    public String lastname = null;
    public boolean active = false;
    public boolean twofactor = false;
    public Player player = null;
    public UUID uuid = null;
    public String playername = "";

    public User() {
        // Конструктор по умолчанию для создания пользователей из новой системы
    }
    
    private User(UUID uuid) {
        this.uuid = uuid;
        this.player = Bukkit.getPlayer(uuid);
        
        // Пытаемся загрузить из старой системы только если файл существует
        File file = new File("plugins/Minetelegram/users/" + uuid + ".yml");
        if (file.exists()) {
            YamlConfiguration userconfig = new YamlConfiguration();
            try {
                userconfig.load(file);
                this.playername = userconfig.getString("playername");
                if (playername == null) playername = "";
                this.chatid = userconfig.getLong("ChatID");
                this.username = userconfig.getString("username");
                this.firstname = userconfig.getString("firstname");
                this.lastname = userconfig.getString("lastname");
                this.twofactor = userconfig.getBoolean("twofactor");
                this.active = userconfig.getBoolean("active");
            } catch (Exception e) {
                // Игнорируем ошибки загрузки старой системы
            }
        }
        
        // Если не загрузили из старой системы, пробуем новую
        if (this.player != null && this.chatid == null) {
            String playername = this.player.getName();
            if (AuthManager.isUserRegistered(playername)) {
                this.playername = playername;
                this.chatid = AuthManager.getTelegramChatId(playername);
                this.active = true;
            }
        }
    }

    public static User getUser(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        // Сначала пробуем старую систему
        try {
            User user = new User(uuid);
            if (user.active) {
                return user;
            }
        } catch (Exception e) {
            // Игнорируем ошибки старой системы
        }

        // Если не найден в старой системе, ищем в новой
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String playername = player.getName();
            if (AuthManager.isUserRegistered(playername)) {
                Long chatId = AuthManager.getTelegramChatId(playername);
                if (chatId != null) {
                    User user = new User();
                    user.uuid = uuid;
                    user.playername = playername;
                    user.chatid = chatId;
                    user.active = true;
                    user.player = player;
                    return user;
                }
            }
        }

        return null;
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

        // Если не найден в новой системе, ищем в старой
        for (User user : getUserList()) {
            if (user.playername.equals(playername)) {
                if (user.active) return user;
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
            Player player = Bukkit.getPlayer(BotTelegram.curentplayer.get(chatid.toString()));
            if (player != null) {
                return getUser(player.getUniqueId());
            }
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