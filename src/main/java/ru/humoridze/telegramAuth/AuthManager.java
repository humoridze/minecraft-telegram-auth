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
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.humoridze.telegramAuth.events.FreezerEvent;
import ru.humoridze.telegramAuth.events.MuterEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class AuthManager {
    private static final String AUTH_DATA_FILE = "plugins/telegramAuth/auth_data.yml";

    private static YamlConfiguration authDataConfig;
    private static Plugin pluginInstance; // Добавляем поле для хранения экземпляра плагина

    // Кэш для максимальной производительности
    private static final ConcurrentHashMap<String, Boolean> registrationCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> whitelistCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> chatIdCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> authStatusCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> tempPasswordCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> lastIpCache = new ConcurrentHashMap<>();

    // Флаг для отслеживания изменений
    private static volatile boolean configChanged = false;

    static {
        loadConfigs();
    }

    // Метод для инициализации плагина
    public static void initialize(Plugin plugin) {
        pluginInstance = plugin;
    }

    private static void loadConfigs() {
        File authDataFile = new File(AUTH_DATA_FILE);
        authDataFile.getParentFile().mkdirs();

        authDataConfig = new YamlConfiguration();
        if (authDataFile.exists()) {
            try {
                authDataConfig.load(authDataFile);
            } catch (IOException | InvalidConfigurationException e) {
                System.out.println("Ошибка загрузки данных авторизации: " + e.getMessage());
            }
        }
    }

    public static void saveConfigs() {
        if (!configChanged) {
            return;
        }

        try {
            authDataConfig.save(new File(AUTH_DATA_FILE));
            configChanged = false;
        } catch (IOException e) {
            System.out.println("Ошибка сохранения конфигурации: " + e.getMessage());
        }
    }

    public static boolean registerUser(String username, String password, Long telegramChatId) {
        try {
            if (isUserRegistered(username)) {
                return false;
            }

            String hashedPassword = PasswordHasher.hashPassword(password);
            String userKey = getUsernameHash(username);

            authDataConfig.set("users." + userKey + ".username", username);
            authDataConfig.set("users." + userKey + ".password", hashedPassword);
            authDataConfig.set("users." + userKey + ".telegram_chat_id", telegramChatId);
            authDataConfig.set("users." + userKey + ".registered", true);
            authDataConfig.set("users." + userKey + ".whitelisted", true);

            registrationCache.put(username, true);
            whitelistCache.put(username, true);
            chatIdCache.put(username, telegramChatId);
            tempPasswordCache.put(username, password);

            configChanged = true;
            saveConfigs();
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка регистрации пользователя: " + e.getMessage());
            return false;
        }
    }

    public static boolean addToWhitelist(String username) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            if (!offlinePlayer.isWhitelisted()) {
                offlinePlayer.setWhitelisted(true);
            }
            String userKey = getUsernameHash(username);
            authDataConfig.set("users." + userKey + ".whitelisted", true);
            configChanged = true;
            whitelistCache.put(username, true);
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка добавления в вайтлист: " + e.getMessage());
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        try {
            String userKey = getUsernameHash(username);
            String storedPassword = authDataConfig.getString("users." + userKey + ".password");
            if (storedPassword == null) {
                return false;
            }
            return PasswordHasher.verifyPassword(password, storedPassword);
        } catch (Exception e) {
            System.out.println("Ошибка аутентификации: " + e.getMessage());
            return false;
        }
    }

    public static boolean isUserRegistered(String username) {
        Boolean cached = registrationCache.get(username);
        if (cached != null) {
            return cached;
        }
        try {
            String userKey = getUsernameHash(username);
            boolean registered = authDataConfig.getBoolean("users." + userKey + ".registered", false);
            registrationCache.put(username, registered);
            return registered;
        } catch (Exception e) {
            registrationCache.put(username, false);
            return false;
        }
    }

    public static boolean isUserWhitelisted(String username) {
        Boolean cached = whitelistCache.get(username);
        if (cached != null) {
            return cached;
        }
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            boolean whitelisted = offlinePlayer.isWhitelisted();
            whitelistCache.put(username, whitelisted);
            return whitelisted;
        } catch (Exception e) {
            whitelistCache.put(username, false);
            return false;
        }
    }

    public static boolean isUserAuthenticated(String username) {
        Boolean cached = authStatusCache.get(username);
        if (cached != null) {
            return cached;
        }
        Player player = Bukkit.getPlayer(username);
        if (player != null) {
            boolean authenticated = !FreezerEvent.isPlayerFrozen(username);
            authStatusCache.put(username, authenticated);
            return authenticated;
        }
        authStatusCache.put(username, false);
        return false;
    }

    public static String getUserPasswordForDisplay(String username) {
        String password = tempPasswordCache.get(username);
        if (password != null) {
            return password;
        }
        return "Пароль не найден";
    }

    public static Long getTelegramChatId(String username) {
        Long cached = chatIdCache.get(username);
        if (cached != null) {
            return cached == 0 ? null : cached;
        }
        try {
            String userKey = getUsernameHash(username);
            Long chatId = authDataConfig.getLong("users." + userKey + ".telegram_chat_id", 0);
            chatIdCache.put(username, chatId);
            return chatId == 0 ? null : chatId;
        } catch (Exception e) {
            chatIdCache.put(username, 0L);
            return null;
        }
    }

    // Новый метод для получения SHA-256 хеша от username
    public static String getUsernameHash(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(username.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    public static void handlePlayerJoin(Player player) {
        String username = player.getName();
        if (!isUserRegistered(username)) {
            player.sendMessage(ChatColor.RED + "Вы не зарегистрированы! https://t.me/stonegladebot");
            player.sendTitle(ChatColor.RED + "Не зарегистрированы!", "https://t.me/stonegladebot", 20, 10000000, 0);
            return;
        }
        if (!isUserWhitelisted(username)) {
            player.sendMessage(ChatColor.RED + "Вы не в вайтлисте! Обратитесь к администратору.");
            player.sendTitle(ChatColor.RED + "Не в вайтлисте!", "Обратитесь к администратору", 20, 10000000, 0);
            return;
        }
        FreezerEvent.freezeplayer(username);
        MuterEvent.mute(username, ChatColor.YELLOW + "Введите пароль для входа: /login <пароль>");
        player.sendMessage(ChatColor.YELLOW + "Введите пароль для входа: /login <пароль>");
        player.sendTitle(ChatColor.YELLOW + "Требуется авторизация", "Введите пароль: /login <пароль>", 20, 10000000, 0);
    }

    public static void handleSuccessfulAuth(Player player) {
        String username = player.getName();
        Long chatId = getTelegramChatId(username);
        if (chatId != null && chatId != 0) {
            sendLoginConfirmation(chatId, username);
            player.sendMessage(ChatColor.YELLOW + "Подтвердите вход через Telegram");
            player.sendTitle(ChatColor.YELLOW + "Подтвердите вход", "через Telegram", 20, 10000000, 0);
        } else {
            player.sendMessage(ChatColor.RED + "Ошибка: не найден Telegram Chat ID!");
            player.sendTitle(ChatColor.RED + "Ошибка", "Не найден Telegram Chat ID", 20, 10000000, 0);
            FreezerEvent.unfreezeplayer(username);
            MuterEvent.unmute(username);
        }
    }

    private static void sendLoginConfirmation(Long chatId, String username) {
        if (TelegramAuth.bot == null) {
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton yesBtn = new InlineKeyboardButton();
        yesBtn.setText("Да");
        yesBtn.setCallbackData("ys" + username);

        InlineKeyboardButton noBtn = new InlineKeyboardButton();
        noBtn.setText("Нет");
        noBtn.setCallbackData("no" + username);

        row.add(yesBtn);
        row.add(noBtn);

        List<List<InlineKeyboardButton>> keyboardList = new ArrayList<>();
        keyboardList.add(row);
        keyboard.setKeyboard(keyboardList);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDD11 " + username + ", это вы пытаетесь войти на сервер?");
        message.setReplyMarkup(keyboard);

        try {
            TelegramAuth.bot.execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    public static void confirmLogin(String username) {
        if (pluginInstance == null) {
            System.out.println("Ошибка: плагин не инициализирован!");
            return;
        }
        Bukkit.getScheduler().runTask(pluginInstance, () -> {
            Player player = Bukkit.getPlayer(username);
            if (player != null) {
                String ip = player.getAddress().getAddress().getHostAddress();
                setLastIp(username, ip);
                FreezerEvent.unfreezeplayer(username);
                MuterEvent.unmute(username);
                player.resetTitle();
                player.sendMessage(ChatColor.GREEN + "Вход подтвержден! Добро пожаловать на сервер!");
                player.sendMessage(ChatColor.YELLOW + "Перед началом игры рекомендуем ознакомиться с правилами: https://humoridze.github.io");
                authStatusCache.put(username, true);
            }
        });
    }

    public static List<String> getRegisteredUsers() {
        List<String> users = new ArrayList<>();
        if (authDataConfig.contains("users")) {
            for (String userKey : authDataConfig.getConfigurationSection("users").getKeys(false)) {
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
        OfflinePlayer[] whitelistedPlayers = Bukkit.getWhitelistedPlayers().toArray(new OfflinePlayer[0]);
        for (OfflinePlayer player : whitelistedPlayers) {
            if (player.getName() != null) {
                users.add(player.getName());
            }
        }
        return users;
    }

    public static boolean removeFromWhitelist(String username) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            if (offlinePlayer.isWhitelisted()) {
                offlinePlayer.setWhitelisted(false);
            }
            String userKey = getUsernameHash(username);
            authDataConfig.set("users." + userKey + ".whitelisted", false);
            configChanged = true;
            whitelistCache.put(username, false);
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка удаления из вайтлиста: " + e.getMessage());
            return false;
        }
    }

    public static String getLastIp(String username) {
        String cached = lastIpCache.get(username);
        if (cached != null) return cached;
        String userKey = getUsernameHash(username);
        String ip = authDataConfig.getString("users." + userKey + ".last_ip");
        if (ip != null) {
            lastIpCache.put(username, ip);
            return ip;
        }
        return null;
    }

    public static void setLastIp(String username, String ip) {
        try {
            String userKey = getUsernameHash(username);
            authDataConfig.set("users." + userKey + ".last_ip", ip);
            configChanged = true;
            lastIpCache.put(username, ip);
        } catch (Exception e) {
            System.out.println("Ошибка сохранения IP: " + e.getMessage());
        }
    }

    // Кикает игрока и меняет пароль на случайный, возвращает новый пароль
    public static String kickAndChangePassword(String username) {
        String newPassword = generateRandomPassword(12);
        String userKey = getUsernameHash(username);
        String hashed = PasswordHasher.hashPassword(newPassword);
        authDataConfig.set("users." + userKey + ".password", hashed);
        configChanged = true;
        Player player = Bukkit.getPlayer(username);
        if (player != null && pluginInstance != null) {
            Bukkit.getScheduler().runTask(pluginInstance, () -> {
                player.kickPlayer("internal exception java.net.socketexception connection reset");
            });
        }
        return newPassword;
    }

    public static boolean changePassword(String username, String newPassword) {
        try {
            String userKey = getUsernameHash(username);
            String hashedPassword = PasswordHasher.hashPassword(newPassword);
            authDataConfig.set("users." + userKey + ".password", hashedPassword);
            configChanged = true;
            saveConfigs();
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка смены пароля: " + e.getMessage());
            return false;
        }
    }

    public static String generateNewPassword(String username) {
        try {
            String newPassword = generateRandomPassword(12);
            String userKey = getUsernameHash(username);
            String hashed = PasswordHasher.hashPassword(newPassword);
            authDataConfig.set("users." + userKey + ".password", hashed);
            configChanged = true;
            saveConfigs();
            return newPassword;
        } catch (Exception e) {
            System.out.println("Ошибка генерации нового пароля: " + e.getMessage());
            return null;
        }
    }

    // Генерация случайного пароля
    private static String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}