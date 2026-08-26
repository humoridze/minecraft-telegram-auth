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
import org.bukkit.plugin.java.JavaPlugin;
import ru.codexdev.telegramAuth.commands.ChangepasswordCMD;
import ru.codexdev.telegramAuth.commands.LoginCMD;
import ru.codexdev.telegramAuth.commands.WhitelistCMD;
import ru.codexdev.telegramAuth.events.GuardEvent;
import ru.codexdev.telegramAuth.events.MuterEvent;
import ru.codexdev.telegramAuth.events.OnJoinEvent;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.TelegramBotsApi;

import java.util.List;

public final class TelegramAuth extends JavaPlugin {
    private static TelegramAuth instance;
    public static BotTelegram bot;

    public static TelegramAuth getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        AuthManager.initialize(this);

        for (String username : AuthManager.getRegisteredUsers()) {
            if (AuthManager.isUserWhitelisted(username)) {
                Bukkit.getOfflinePlayer(username).setWhitelisted(true);
            }
        }

        Bukkit.getPluginManager().registerEvents(new OnJoinEvent(), this);
        Bukkit.getPluginManager().registerEvents(new FreezerEvent(), this);
        Bukkit.getPluginManager().registerEvents(new MuterEvent(), this);
        Bukkit.getPluginManager().registerEvents(new ru.codexdev.telegramAuth.events.PlayerLoginEvent(), this);
        Bukkit.getPluginManager().registerEvents(new GuardEvent(), this);

        getCommand("login").setExecutor(new LoginCMD());
        getCommand("changepassword").setExecutor(new ChangepasswordCMD());
        getCommand("whitelist").setExecutor(new WhitelistCMD());

        String botToken = getConfig().getString("token", "changeme");
        String botUsername = getConfig().getString("username", "changeme");

        if ("changeme".equals(botToken) || "changeme".equals(botUsername)
                || botToken == null || botUsername == null
                || botToken.isEmpty() || botUsername.isEmpty()) {
            getLogger().warning("Укажите token и username бота в plugins/TelegramAuth/config.yml");
            getLogger().warning("Бот отключен, пока конфиг не будет заполнен");
        } else {
            try {
                bot = new BotTelegram(botUsername, botToken);
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                botsApi.registerBot(bot);
                getLogger().info("Telegram-бот зарегистрирован: @" + botUsername);
            } catch (TelegramApiException e) {
                getLogger().severe("Не удалось зарегистрировать Telegram-бота: " + e.getMessage());
                getLogger().severe("Проверьте token и username в plugins/TelegramAuth/config.yml");
                bot = null;
            }
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, AuthManager::saveConfigs, 600L, 600L);

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            AuthManager.handlePlayerJoin(player);
        }
    }

    @Override
    public void onDisable() {
        AuthManager.shutdownSessions();
        AuthManager.saveConfigs();
        if (bot != null) {
            bot.onClosing();
            bot = null;
        }
        instance = null;
        getLogger().info("Plugin has been disabled");
    }

    public String getServerIp() {
        return getConfig().getString("server-ip", "changeme");
    }

    public String getTelegramLink() {
        return getConfig().getString("telegram-link", "changeme");
    }

    public String getRulesUrl() {
        return getConfig().getString("rules-url", "changeme");
    }

    public int getMinPasswordLength() {
        return Math.max(1, getConfig().getInt("min-password-length", 6));
    }

    public int getLoginTimeoutSeconds() {
        return Math.max(10, getConfig().getInt("login-timeout-seconds", 60));
    }

    public int getMaxLoginAttempts() {
        return Math.max(1, getConfig().getInt("max-login-attempts", 5));
    }

    public boolean skipPasswordOnSameIp() {
        return getConfig().getBoolean("skip-password-on-same-ip", true);
    }

    public boolean isTelegramAdmin(long chatId) {
        List<Long> adminIds = getConfig().getLongList("admin-telegram-ids");
        return adminIds.contains(chatId);
    }
}
