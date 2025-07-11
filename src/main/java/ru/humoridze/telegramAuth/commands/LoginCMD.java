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
import ru.humoridze.telegramAuth.events.FreezerEvent;

public class LoginCMD implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок!");
            return true;
        }

        if (strings.length == 0) {
            commandSender.sendMessage(ChatColor.RED + "Использование: /login <пароль>");
            return true;
        }

        Player p = (Player) commandSender;
        String username = p.getName();
        String password = strings[0];

        // Проверяем, не авторизован ли уже игрок
        if (AuthManager.isUserAuthenticated(username) && !FreezerEvent.isPlayerFrozen(username)) {
            p.sendMessage(ChatColor.GREEN + "Вы уже авторизованы!");
            return true;
        }

        if (!AuthManager.isUserRegistered(username)) {
            p.sendMessage(ChatColor.RED + "Вы не зарегистрированы! Зарегистрируйтесь через Telegram бота.");
            return true;
        }


        if (AuthManager.authenticateUser(username, password)) {
            AuthManager.handleSuccessfulAuth(p);
        } else {
            p.sendMessage(ChatColor.RED + "Неверный пароль!");
        }

        return true;
    }
}