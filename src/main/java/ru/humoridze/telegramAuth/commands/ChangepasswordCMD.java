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

public class ChangepasswordCMD implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок!");
            return true;
        }

        Player player = (Player) commandSender;
        String username = player.getName();

        if (strings.length != 3) {
            player.sendMessage(ChatColor.RED + "Использование: /changepassword <старый пароль> <новый пароль> <повторите пароль>");
            return true;
        }

        String oldPassword = strings[0];
        String newPassword = strings[1];
        String confirmPassword = strings[2];

        // Проверяем, зарегистрирован ли игрок
        if (!AuthManager.isUserRegistered(username)) {
            player.sendMessage(ChatColor.RED + "Вы не зарегистрированы! Зарегистрируйтесь через Telegram бота.");
            return true;
        }

        // Проверяем, совпадают ли новый пароль и подтверждение
        if (!newPassword.equals(confirmPassword)) {
            player.sendMessage(ChatColor.RED + "Новые пароли не совпадают!");
            return true;
        }

        // Проверяем длину нового пароля
        if (newPassword.length() < 6) {
            player.sendMessage(ChatColor.RED + "Новый пароль должен содержать минимум 6 символов!");
            return true;
        }

        // Проверяем старый пароль
        if (!AuthManager.authenticateUser(username, oldPassword)) {
            player.sendMessage(ChatColor.RED + "Неверный старый пароль!");
            return true;
        }

        // Меняем пароль через AuthManager
        if (AuthManager.changePassword(username, newPassword)) {
            player.sendMessage(ChatColor.GREEN + "Пароль успешно изменен!");
            
            // Отправляем уведомление в Telegram с кнопкой
            Long chatId = AuthManager.getTelegramChatId(username);
            if (chatId != null && ru.humoridze.telegramAuth.TelegramAuth.bot != null) {
                ru.humoridze.telegramAuth.TelegramAuth.bot.sendPasswordChangeNotification(chatId, username);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Ошибка при изменении пароля. Попробуйте еще раз.");
        }

        return true;
    }
}
