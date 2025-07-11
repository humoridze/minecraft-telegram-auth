/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.humoridze.telegramAuth.commands.*;
import ru.humoridze.telegramAuth.commands.WhitelistCMD;
import ru.humoridze.telegramAuth.events.OnJoinEvent;
import ru.humoridze.telegramAuth.events.FreezerEvent;
import ru.humoridze.telegramAuth.events.MuterEvent;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.TelegramBotsApi;

public final class TelegramAuth extends JavaPlugin {
    public static BotTelegram bot;

    @Override
    public void onEnable() {
        System.out.println("[TelegramAuth] Plugin has been enabled");

        // Инициализируем AuthManager с экземпляром плагина
        AuthManager.initialize(this);

        Bukkit.getServer().getPluginManager().registerEvents(new OnJoinEvent(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new FreezerEvent(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new MuterEvent(), this);
        Bukkit.getServer().getPluginManager().registerEvents(new ru.humoridze.telegramAuth.events.PlayerLoginEvent(), this);
        Handler handler = new Handler();
        handler.runTaskTimer(this,0,1);
        getCommand("login").setExecutor(new LoginCMD());
        getCommand("changepassword").setExecutor(new ChangepasswordCMD());
        getCommand("whitelist").setExecutor(new WhitelistCMD());

        // Инициализация бота
        bot = new BotTelegram();
        String botToken = bot.getBotToken();
        String botUsername = bot.getBotUsername();

        if ("changeme".equals(botToken) || "changeme".equals(botUsername)) {
            System.out.println("[TelegramAuth] WARNING: Please set your bot token and username in plugins/telegramAuth/config.yml");
            System.out.println("[TelegramAuth] Bot functionality will be disabled until configured properly");
        } else {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                botsApi.registerBot(bot);
                System.out.println("[TelegramAuth] Telegram bot registered successfully: @" + botUsername);
            } catch (TelegramApiException e) {
                System.out.println("[TelegramAuth] ERROR: Failed to register Telegram bot: " + e.getMessage());
                System.out.println("[TelegramAuth] Please check your bot token and username in plugins/telegramAuth/config.yml");
                bot = null; // Устанавливаем бота в null при ошибке
            }
        }

        // Запускаем задачу для периодического сохранения конфигурации
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            AuthManager.saveConfigs();
        }, 600L, 600L); // Каждые 30 секунд (600 тиков)
    }

    @Override
    public void onDisable() {
        // Сохраняем конфигурацию при выключении плагина
        AuthManager.saveConfigs();
        System.out.println("[TelegramAuth] Plugin has been disabled");
    }
}