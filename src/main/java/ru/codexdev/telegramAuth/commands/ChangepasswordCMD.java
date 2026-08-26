/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.codexdev.telegramAuth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.codexdev.telegramAuth.AuthManager;
import ru.codexdev.telegramAuth.TelegramAuth;

public class ChangepasswordCMD implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок!");
            return true;
        }

        Player player = (Player) commandSender;
        String username = player.getName();

        if (!AuthManager.isUserAuthenticated(username)) {
            player.sendMessage(ChatColor.RED + "Сначала авторизуйтесь: /login <пароль>");
            return true;
        }

        if (strings.length != 3) {
            player.sendMessage(ChatColor.RED + "Использование: /changepassword <старый пароль> <новый пароль> <повторите пароль>");
            return true;
        }

        String oldPassword = strings[0];
        String newPassword = strings[1];
        String confirmPassword = strings[2];

        if (!AuthManager.isUserRegistered(username)) {
            player.sendMessage(ChatColor.RED + "Вы не зарегистрированы! Зарегистрируйтесь через Telegram бота.");
            return true;
        }

        if (!newPassword.equals(confirmPassword)) {
            player.sendMessage(ChatColor.RED + "Новые пароли не совпадают!");
            return true;
        }

        int minLength = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getMinPasswordLength()
                : 6;
        if (newPassword.length() < minLength) {
            player.sendMessage(ChatColor.RED + "Новый пароль должен содержать минимум " + minLength + " символов!");
            return true;
        }

        if (!AuthManager.authenticateUser(username, oldPassword)) {
            player.sendMessage(ChatColor.RED + "Неверный старый пароль!");
            return true;
        }

        if (AuthManager.changePassword(username, newPassword)) {
            player.sendMessage(ChatColor.GREEN + "Пароль успешно изменен!");
            Long chatId = AuthManager.getTelegramChatId(username);
            if (chatId != null && TelegramAuth.bot != null) {
                TelegramAuth.bot.sendPasswordChangeNotification(chatId, username);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Ошибка при изменении пароля. Попробуйте еще раз.");
        }

        return true;
    }
}
