/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.humoridze.telegramAuth.AuthManager;

public class LoginCMD implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок!");
            return true;
        }

        Player player = (Player) commandSender;
        String username = player.getName();

        if (AuthManager.isUserAuthenticated(username)) {
            player.sendMessage(ChatColor.GREEN + "Вы уже авторизованы!");
            return true;
        }

        if (AuthManager.isPendingTelegramConfirm(username)) {
            player.sendMessage(ChatColor.YELLOW + "Пароль принят. Подтвердите вход через Telegram.");
            return true;
        }

        if (strings.length == 0) {
            player.sendMessage(ChatColor.RED + "Использование: /login <пароль>");
            return true;
        }

        if (!AuthManager.isUserRegistered(username)) {
            player.sendMessage(ChatColor.RED + "Вы не зарегистрированы! Зарегистрируйтесь через Telegram бота.");
            return true;
        }

        String password = strings[0];
        if (AuthManager.authenticateUser(username, password)) {
            AuthManager.handleSuccessfulAuth(player);
        } else {
            boolean kicked = AuthManager.registerFailedLogin(username);
            if (!kicked) {
                player.sendMessage(ChatColor.RED + "Неверный пароль! Осталось попыток: "
                        + AuthManager.remainingLoginAttempts(username));
            }
        }

        return true;
    }
}
