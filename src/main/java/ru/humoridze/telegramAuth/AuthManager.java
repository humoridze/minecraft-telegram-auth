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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {
    private static final String AUTH_DATA_FILE = "plugins/telegramAuth/auth_data.yml";

    private static YamlConfiguration authDataConfig;
    private static Plugin pluginInstance; // Добавляем поле для хранения экземпляра плагина

    // Кэш для максимальной производительности
    private static final ConcurrentHashMap<String, String> usernameToUuidCache = new ConcurrentHashMap<>();
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
            String uuid = getPlayerUUID(username);
            if (uuid == null) {
                uuid = "temp_" + username.toLowerCase().replaceAll("[^a-z0-9]", "");
            }

            authDataConfig.set("users." + uuid + ".username", username);
            authDataConfig.set("users." + uuid + ".password", hashedPassword);
            authDataConfig.set("users." + uuid + ".telegram_chat_id", telegramChatId);
            authDataConfig.set("users." + uuid + ".registered", true);
            authDataConfig.set("users." + uuid + ".whitelisted", true);

            // Обновляем кэш
            usernameToUuidCache.put(username, uuid);
            registrationCache.put(username, true);
            whitelistCache.put(username, true);
            chatIdCache.put(username, telegramChatId);

            // Сохраняем пароль во временном кэше для показа в спойлере
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
            // Добавляем в Bukkit whitelist
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            if (!offlinePlayer.isWhitelisted()) {
                offlinePlayer.setWhitelisted(true);
            }

            // Обновляем значение в конфигурации
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                authDataConfig.set("users." + uuid + ".whitelisted", true);
                configChanged = true;
            }

            whitelistCache.put(username, true);
            return true;
        } catch (Exception e) {
            System.out.println("Ошибка добавления в вайтлист: " + e.getMessage());
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        try {
            String uuid = getPlayerUUID(username);
            String storedPassword = null;

            if (uuid != null) {
                storedPassword = authDataConfig.getString("users." + uuid + ".password");
            }

            if (storedPassword == null) {
                if (authDataConfig.contains("users")) {
                    for (String userUuid : authDataConfig.getConfigurationSection("users").getKeys(false)) {
                        String storedUsername = authDataConfig.getString("users." + userUuid + ".username");
                        if (username.equals(storedUsername)) {
                            storedPassword = authDataConfig.getString("users." + userUuid + ".password");
                            break;
                        }
                    }
                }
            }

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
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                boolean registered = authDataConfig.getBoolean("users." + uuid + ".registered", false);
                registrationCache.put(username, registered);
                return registered;
            }

            if (authDataConfig.contains("users")) {
                for (String userUuid : authDataConfig.getConfigurationSection("users").getKeys(false)) {
                    String storedUsername = authDataConfig.getString("users." + userUuid + ".username");
                    if (username.equals(storedUsername)) {
                        boolean registered = authDataConfig.getBoolean("users." + userUuid + ".registered", false);
                        registrationCache.put(username, registered);
                        return registered;
                    }
                }
            }

            registrationCache.put(username, false);
            return false;
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
            // Проверяем Bukkit whitelist
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

        // Проверяем, есть ли игрок онлайн и не заморожен ли он
        Player player = Bukkit.getPlayer(username);
        if (player != null) {
            // Если игрок может двигаться, значит он авторизован
            boolean authenticated = !FreezerEvent.isPlayerFrozen(username);
            authStatusCache.put(username, authenticated);
            return authenticated;
        }

        // Если игрок офлайн, считаем что не авторизован
        authStatusCache.put(username, false);
        return false;
    }

    public static String getUserPasswordForDisplay(String username) {
        // Возвращаем пароль из временного кэша
        String password = tempPasswordCache.get(username);
        if (password != null) {
            return password;
        }

        // Если пароль не найден в кэше, возвращаем сообщение
        return "Пароль не найден";
    }

    public static Long getTelegramChatId(String username) {
        Long cached = chatIdCache.get(username);
        if (cached != null) {
            return cached == 0 ? null : cached;
        }

        try {
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                Long chatId = authDataConfig.getLong("users." + uuid + ".telegram_chat_id", 0);
                chatIdCache.put(username, chatId);
                return chatId == 0 ? null : chatId;
            }

            if (authDataConfig.contains("users")) {
                for (String userUuid : authDataConfig.getConfigurationSection("users").getKeys(false)) {
                    String storedUsername = authDataConfig.getString("users." + userUuid + ".username");
                    if (username.equals(storedUsername)) {
                        Long chatId = authDataConfig.getLong("users." + userUuid + ".telegram_chat_id", 0);
                        chatIdCache.put(username, chatId);
                        return chatId == 0 ? null : chatId;
                    }
                }
            }

            chatIdCache.put(username, 0L);
            return null;
        } catch (Exception e) {
            chatIdCache.put(username, 0L);
            return null;
        }
    }

    private static String getPlayerUUID(String username) {
        String cached = usernameToUuidCache.get(username);
        if (cached != null) {
            return cached;
        }

        Player onlinePlayer = Bukkit.getPlayer(username);
        if (onlinePlayer != null) {
            String uuid = onlinePlayer.getUniqueId().toString();
            usernameToUuidCache.put(username, uuid);
            return uuid;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) {
            String uuid = offlinePlayer.getUniqueId().toString();
            usernameToUuidCache.put(username, uuid);
            return uuid;
        }

        return null;
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

        // Пользователь зарегистрирован и в вайтлисте, но требует авторизации
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

            // Если нет связи с Telegram, разморозим игрока после стандартной авторизации
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
                // Сохраняем IP после подтверждения
                String ip = player.getAddress().getAddress().getHostAddress();
                setLastIp(username, ip);

                FreezerEvent.unfreezeplayer(username);
                MuterEvent.unmute(username);
                player.resetTitle();
                player.sendMessage(ChatColor.GREEN + "Вход подтвержден! Добро пожаловать на сервер!");
                authStatusCache.put(username, true);
            }
        });
    }

    public static List<String> getRegisteredUsers() {
        List<String> users = new ArrayList<>();
        if (authDataConfig.contains("users")) {
            for (String uuid : authDataConfig.getConfigurationSection("users").getKeys(false)) {
                String username = authDataConfig.getString("users." + uuid + ".username");
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

            // Обновляем значение в конфигурации
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                authDataConfig.set("users." + uuid + ".whitelisted", false);
                configChanged = true;
            }

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

        String uuid = getPlayerUUID(username);
        if (uuid != null) {
            String ip = authDataConfig.getString("users." + uuid + ".last_ip");
            if (ip != null) {
                lastIpCache.put(username, ip);
                return ip;
            }
        }
        return null;
    }

    public static void setLastIp(String username, String ip) {
        try {
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                authDataConfig.set("users." + uuid + ".last_ip", ip);
                configChanged = true;
            }
            lastIpCache.put(username, ip);
        } catch (Exception e) {
            System.out.println("Ошибка сохранения IP: " + e.getMessage());
        }
    }

    // Кикает игрока и меняет пароль на случайный, возвращает новый пароль
    public static String kickAndChangePassword(String username) {
        String newPassword = generateRandomPassword(12);
        String uuid = getPlayerUUID(username);
        if (uuid != null) {
            // Хешируем новый пароль
            String hashed = PasswordHasher.hashPassword(newPassword);
            authDataConfig.set("users." + uuid + ".password", hashed);
            configChanged = true;
        }
        // Кикаем игрока, если он онлайн, только в основном потоке
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
            String uuid = getPlayerUUID(username);
            if (uuid == null) {
                return false;
            }

            // Хешируем новый пароль
            String hashedPassword = PasswordHasher.hashPassword(newPassword);
            authDataConfig.set("users." + uuid + ".password", hashedPassword);
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
            String uuid = getPlayerUUID(username);
            if (uuid != null) {
                // Хешируем новый пароль
                String hashed = PasswordHasher.hashPassword(newPassword);
                authDataConfig.set("users." + uuid + ".password", hashed);
                configChanged = true;
                saveConfigs();
            }
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