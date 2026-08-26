/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.codexdev.telegramAuth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.codexdev.telegramAuth.events.FreezerEvent;
import ru.codexdev.telegramAuth.events.MuterEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private static final Object CONFIG_LOCK = new Object();
    private static File authDataFile;
    private static YamlConfiguration authDataConfig;
    private static Plugin pluginInstance;

    private static final ConcurrentHashMap<String, Boolean> registrationCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> whitelistCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> chatIdCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> authStatusCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> pendingTelegramCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> lastIpCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> loginAttempts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, BukkitTask> loginTimeouts = new ConcurrentHashMap<>();

    private static volatile boolean configChanged = false;

    public static void initialize(Plugin plugin) {
        pluginInstance = plugin;
        loadConfigs();
    }

    private static String cacheKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static void loadConfigs() {
        authDataFile = new File(pluginInstance.getDataFolder(), "auth_data.yml");
        authDataFile.getParentFile().mkdirs();
        authDataConfig = new YamlConfiguration();
        if (authDataFile.exists()) {
            try {
                authDataConfig.load(authDataFile);
            } catch (IOException | InvalidConfigurationException e) {
                pluginInstance.getLogger().severe("Ошибка загрузки данных авторизации: " + e.getMessage());
            }
        }
        warmCaches();
    }

    private static void warmCaches() {
        ConfigurationSection usersSection;
        synchronized (CONFIG_LOCK) {
            usersSection = authDataConfig.getConfigurationSection("users");
        }
        if (usersSection == null) {
            return;
        }
        for (String userKey : usersSection.getKeys(false)) {
            String username;
            boolean registered;
            boolean whitelisted;
            long chatId;
            String lastIp;
            synchronized (CONFIG_LOCK) {
                username = authDataConfig.getString("users." + userKey + ".username");
                registered = authDataConfig.getBoolean("users." + userKey + ".registered", false);
                whitelisted = authDataConfig.getBoolean("users." + userKey + ".whitelisted", false);
                chatId = authDataConfig.getLong("users." + userKey + ".telegram_chat_id", 0);
                lastIp = authDataConfig.getString("users." + userKey + ".last_ip");
            }
            if (username == null) {
                continue;
            }
            String key = cacheKey(username);
            registrationCache.put(key, registered);
            whitelistCache.put(key, whitelisted);
            chatIdCache.put(key, chatId);
            if (lastIp != null) {
                lastIpCache.put(key, lastIp);
            }
        }
    }

    public static void saveConfigs() {
        if (!configChanged || authDataFile == null) {
            return;
        }
        synchronized (CONFIG_LOCK) {
            if (!configChanged) {
                return;
            }
            try {
                authDataConfig.save(authDataFile);
                configChanged = false;
            } catch (IOException e) {
                if (pluginInstance != null) {
                    pluginInstance.getLogger().severe("Ошибка сохранения конфигурации: " + e.getMessage());
                }
            }
        }
    }

    public static boolean registerUser(String username, String password, Long telegramChatId) {
        if (isUserRegistered(username)) {
            return false;
        }
        String hashedPassword = PasswordHasher.hashPassword(password);
        String userKey = getUsernameHash(username);
        String key = cacheKey(username);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".username", username);
            authDataConfig.set("users." + userKey + ".password", hashedPassword);
            authDataConfig.set("users." + userKey + ".telegram_chat_id", telegramChatId);
            authDataConfig.set("users." + userKey + ".registered", true);
            authDataConfig.set("users." + userKey + ".whitelisted", true);
            configChanged = true;
        }
        registrationCache.put(key, true);
        whitelistCache.put(key, true);
        chatIdCache.put(key, telegramChatId);
        saveConfigs();
        return true;
    }

    public static boolean addToWhitelist(String username) {
        String userKey = getUsernameHash(username);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".whitelisted", true);
            configChanged = true;
        }
        whitelistCache.put(cacheKey(username), true);
        setBukkitWhitelist(username, true);
        return true;
    }

    private static void setBukkitWhitelist(final String username, final boolean allowed) {
        if (pluginInstance == null) {
            return;
        }
        Runnable updateWhitelist = () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            if (offlinePlayer.isWhitelisted() != allowed) {
                offlinePlayer.setWhitelisted(allowed);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            updateWhitelist.run();
        } else {
            Bukkit.getScheduler().runTask(pluginInstance, updateWhitelist);
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String userKey = getUsernameHash(username);
        String storedPassword;
        synchronized (CONFIG_LOCK) {
            storedPassword = authDataConfig.getString("users." + userKey + ".password");
        }
        if (storedPassword == null) {
            return false;
        }
        return PasswordHasher.verifyPassword(password, storedPassword);
    }

    public static boolean isUserRegistered(String username) {
        String key = cacheKey(username);
        Boolean cached = registrationCache.get(key);
        if (cached != null) {
            return cached;
        }
        String userKey = getUsernameHash(username);
        boolean registered;
        synchronized (CONFIG_LOCK) {
            registered = authDataConfig.getBoolean("users." + userKey + ".registered", false);
        }
        registrationCache.put(key, registered);
        return registered;
    }

    public static boolean isUserWhitelisted(String username) {
        String key = cacheKey(username);
        Boolean cached = whitelistCache.get(key);
        if (cached != null) {
            return cached;
        }
        String userKey = getUsernameHash(username);
        boolean whitelisted;
        synchronized (CONFIG_LOCK) {
            whitelisted = authDataConfig.getBoolean("users." + userKey + ".whitelisted", false);
        }
        whitelistCache.put(key, whitelisted);
        return whitelisted;
    }

    public static boolean isUserAuthenticated(String username) {
        String key = cacheKey(username);
        Boolean cached = authStatusCache.get(key);
        if (cached != null) {
            return cached;
        }
        return false;
    }

    public static boolean isPendingTelegramConfirm(String username) {
        return Boolean.TRUE.equals(pendingTelegramCache.get(cacheKey(username)));
    }

    public static Long getTelegramChatId(String username) {
        String key = cacheKey(username);
        Long cached = chatIdCache.get(key);
        if (cached != null) {
            return cached == 0 ? null : cached;
        }
        String userKey = getUsernameHash(username);
        long chatId;
        synchronized (CONFIG_LOCK) {
            chatId = authDataConfig.getLong("users." + userKey + ".telegram_chat_id", 0);
        }
        chatIdCache.put(key, chatId);
        return chatId == 0 ? null : chatId;
    }

    public static String getUsernameHash(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    public static void handlePlayerJoin(Player player) {
        String username = player.getName();
        TelegramAuth plugin = TelegramAuth.getInstance();
        String telegramLink = plugin != null ? plugin.getTelegramLink() : "changeme";
        if (!isUserRegistered(username)) {
            player.sendMessage(ChatColor.RED + "Вы не зарегистрированы! " + telegramLink);
            player.sendTitle(ChatColor.RED + "Не зарегистрированы!", telegramLink, 20, 10000000, 0);
            return;
        }
        if (!isUserWhitelisted(username)) {
            player.sendMessage(ChatColor.RED + "Вы не в вайтлисте! Обратитесь к администратору.");
            player.sendTitle(ChatColor.RED + "Не в вайтлисте!", "Обратитесь к администратору", 20, 10000000, 0);
            return;
        }
        beginAuthSession(player, ChatColor.YELLOW + "Введите пароль для входа: /login <пароль>",
                ChatColor.YELLOW + "Требуется авторизация", "Введите пароль: /login <пароль>");
    }

    public static void beginAuthSession(Player player, String chatMessage, String title, String subtitle) {
        String username = player.getName();
        authStatusCache.put(cacheKey(username), false);
        pendingTelegramCache.remove(cacheKey(username));
        FreezerEvent.freezeplayer(username);
        MuterEvent.mute(username, chatMessage);
        player.sendMessage(chatMessage);
        player.sendTitle(title, subtitle, 20, 10000000, 0);
        startLoginTimeout(username);
    }

    public static void handleSuccessfulAuth(Player player) {
        String username = player.getName();
        Long chatId = getTelegramChatId(username);
        if (chatId == null || chatId == 0) {
            player.kickPlayer(ChatColor.RED + "Ошибка: не найден Telegram Chat ID!");
            return;
        }
        if (!FreezerEvent.isPlayerFrozen(username)) {
            FreezerEvent.freezeplayer(username);
            startLoginTimeout(username);
        }
        pendingTelegramCache.put(cacheKey(username), true);
        resetLoginAttempts(username);
        sendLoginConfirmation(chatId, username);
        player.sendMessage(ChatColor.YELLOW + "Подтвердите вход через Telegram");
        player.sendTitle(ChatColor.YELLOW + "Подтвердите вход", "через Telegram", 20, 10000000, 0);
        MuterEvent.mute(username, ChatColor.YELLOW + "Ожидается подтверждение через Telegram");
    }

    private static void sendLoginConfirmation(final Long chatId, final String username) {
        if (TelegramAuth.bot == null || pluginInstance == null) {
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton yesBtn = new InlineKeyboardButton();
        yesBtn.setText("Да");
        yesBtn.setCallbackData("yes:" + username);

        InlineKeyboardButton noBtn = new InlineKeyboardButton();
        noBtn.setText("Нет");
        noBtn.setCallbackData("no:" + username);

        row.add(yesBtn);
        row.add(noBtn);

        List<List<InlineKeyboardButton>> keyboardList = new ArrayList<>();
        keyboardList.add(row);
        keyboard.setKeyboard(keyboardList);

        final SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDD11 " + username + ", это вы пытаетесь войти на сервер?");
        message.setReplyMarkup(keyboard);

        Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, () -> {
            try {
                TelegramAuth.bot.execute(message);
            } catch (TelegramApiException e) {
                pluginInstance.getLogger().warning("Ошибка отправки сообщения: " + e.getMessage());
            }
        });
    }

    public static void confirmLogin(String username) {
        if (pluginInstance == null) {
            return;
        }
        Bukkit.getScheduler().runTask(pluginInstance, () -> {
            if (!Boolean.TRUE.equals(pendingTelegramCache.remove(cacheKey(username)))) {
                return;
            }
            Player player = Bukkit.getPlayer(username);
            if (player == null) {
                return;
            }
            String currentIp = null;
            if (player.getAddress() != null) {
                currentIp = player.getAddress().getAddress().getHostAddress();
                setLastIp(username, currentIp);
            }
            FreezerEvent.unfreezeplayer(username);
            MuterEvent.unmute(username);
            authStatusCache.put(cacheKey(username), true);
            cancelLoginTimeout(username);
            resetLoginAttempts(username);
            player.resetTitle();
            player.sendMessage(ChatColor.GREEN + "Вход подтвержден! Добро пожаловать на сервер!");
            String rulesUrl = TelegramAuth.getInstance() != null
                    ? TelegramAuth.getInstance().getRulesUrl()
                    : "changeme";
            player.sendMessage(ChatColor.YELLOW + "Перед началом игры рекомендуем ознакомиться с правилами: " + rulesUrl);
            final Long chatId = getTelegramChatId(username);
            final String confirmedIp = currentIp;
            if (chatId != null && TelegramAuth.bot != null) {
                Bukkit.getScheduler().runTaskAsynchronously(pluginInstance, () ->
                        TelegramAuth.bot.sendSuccessLogin(chatId, username, confirmedIp));
            }
        });
    }

    public static void clearSession(String username) {
        String key = cacheKey(username);
        authStatusCache.remove(key);
        pendingTelegramCache.remove(key);
        loginAttempts.remove(key);
        cancelLoginTimeout(username);
        FreezerEvent.unfreezeplayer(username);
        MuterEvent.unmute(username);
    }

    public static void startLoginTimeout(String username) {
        cancelLoginTimeout(username);
        if (pluginInstance == null) {
            return;
        }
        int seconds = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getLoginTimeoutSeconds()
                : 60;
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
            Player player = Bukkit.getPlayer(username);
            if (player != null && FreezerEvent.isPlayerFrozen(username)) {
                player.kickPlayer(ChatColor.RED + "Время авторизации истекло");
            }
        }, seconds * 20L);
        loginTimeouts.put(cacheKey(username), timeoutTask);
    }

    public static void cancelLoginTimeout(String username) {
        BukkitTask timeoutTask = loginTimeouts.remove(cacheKey(username));
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
    }

    public static void shutdownSessions() {
        for (BukkitTask timeoutTask : loginTimeouts.values()) {
            timeoutTask.cancel();
        }
        loginTimeouts.clear();
        authStatusCache.clear();
        pendingTelegramCache.clear();
        loginAttempts.clear();
    }

    public static boolean registerFailedLogin(String username) {
        String key = cacheKey(username);
        int attempts = loginAttempts.getOrDefault(key, 0) + 1;
        loginAttempts.put(key, attempts);
        int maxAttempts = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getMaxLoginAttempts()
                : 5;
        if (attempts >= maxAttempts) {
            Handler.kick(username, ChatColor.RED + "Слишком много неверных попыток входа");
            return true;
        }
        return false;
    }

    public static void resetLoginAttempts(String username) {
        loginAttempts.remove(cacheKey(username));
    }

    public static int remainingLoginAttempts(String username) {
        int maxAttempts = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getMaxLoginAttempts()
                : 5;
        int used = loginAttempts.getOrDefault(cacheKey(username), 0);
        return Math.max(0, maxAttempts - used);
    }

    public static List<String> getRegisteredUsers() {
        List<String> users = new ArrayList<>();
        ConfigurationSection usersSection;
        synchronized (CONFIG_LOCK) {
            usersSection = authDataConfig.getConfigurationSection("users");
            if (usersSection == null) {
                return users;
            }
            for (String userKey : usersSection.getKeys(false)) {
                String username = authDataConfig.getString("users." + userKey + ".username");
                if (username != null) {
                    users.add(username);
                }
            }
        }
        return users;
    }

    public static List<String> getWhitelistedUsers() {
        List<String> users = new ArrayList<>();
        for (String username : getRegisteredUsers()) {
            if (isUserWhitelisted(username)) {
                users.add(username);
            }
        }
        return users;
    }

    public static boolean removeFromWhitelist(String username) {
        setBukkitWhitelist(username, false);
        String userKey = getUsernameHash(username);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".whitelisted", false);
            configChanged = true;
        }
        whitelistCache.put(cacheKey(username), false);
        Handler.kick(username, ChatColor.RED + "Вы удалены из вайтлиста");
        return true;
    }

    public static String getLastIp(String username) {
        String key = cacheKey(username);
        String cached = lastIpCache.get(key);
        if (cached != null) {
            return cached;
        }
        String userKey = getUsernameHash(username);
        String ip;
        synchronized (CONFIG_LOCK) {
            ip = authDataConfig.getString("users." + userKey + ".last_ip");
        }
        if (ip != null) {
            lastIpCache.put(key, ip);
        }
        return ip;
    }

    public static void setLastIp(String username, String ip) {
        String userKey = getUsernameHash(username);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".last_ip", ip);
            configChanged = true;
        }
        lastIpCache.put(cacheKey(username), ip);
    }

    public static String kickAndChangePassword(String username) {
        String newPassword = generateRandomPassword(12);
        String userKey = getUsernameHash(username);
        String hashed = PasswordHasher.hashPassword(newPassword);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".password", hashed);
            configChanged = true;
        }
        saveConfigs();
        pendingTelegramCache.remove(cacheKey(username));
        authStatusCache.put(cacheKey(username), false);
        if (pluginInstance != null) {
            Bukkit.getScheduler().runTask(pluginInstance, () -> {
                Player player = Bukkit.getPlayer(username);
                if (player != null) {
                    player.kickPlayer("internal exception java.net.socketexception connection reset");
                }
            });
        }
        return newPassword;
    }

    public static boolean changePassword(String username, String newPassword) {
        String userKey = getUsernameHash(username);
        String hashedPassword = PasswordHasher.hashPassword(newPassword);
        synchronized (CONFIG_LOCK) {
            authDataConfig.set("users." + userKey + ".password", hashedPassword);
            configChanged = true;
        }
        saveConfigs();
        return true;
    }

    public static String generateNewPassword(String username) {
        String newPassword = generateRandomPassword(12);
        if (changePassword(username, newPassword)) {
            return newPassword;
        }
        return null;
    }

    private static String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }
}
