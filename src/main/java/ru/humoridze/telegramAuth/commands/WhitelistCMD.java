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
import ru.humoridze.telegramAuth.AuthManager;

import java.util.List;

public class WhitelistCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("telegramAuth.whitelist")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав для использования этой команды!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Использование: /whitelist <add/remove/list> [игрок]");
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /whitelist add <игрок>");
                    return true;
                }
                handleAdd(sender, args[1]);
                break;

            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /whitelist remove <игрок>");
                    return true;
                }
                handleRemove(sender, args[1]);
                break;

            case "list":
                handleList(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда. Используйте: add, remove, list");
                break;
        }

        return true;
    }

    private void handleAdd(CommandSender sender, String username) {
        if (!AuthManager.isUserRegistered(username)) {
            sender.sendMessage(ChatColor.RED + "Игрок " + username + " не зарегистрирован!");
            return;
        }

        if (AuthManager.isUserWhitelisted(username)) {
            sender.sendMessage(ChatColor.YELLOW + "Игрок " + username + " уже в вайтлисте!");
            return;
        }

        if (AuthManager.addToWhitelist(username)) {
            sender.sendMessage(ChatColor.GREEN + "Игрок " + username + " добавлен в вайтлист!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка добавления игрока " + username + " в вайтлист!");
        }
    }

    private void handleRemove(CommandSender sender, String username) {
        if (!AuthManager.isUserWhitelisted(username)) {
            sender.sendMessage(ChatColor.YELLOW + "Игрок " + username + " не в вайтлисте!");
            return;
        }
        if (AuthManager.removeFromWhitelist(username)) {
            sender.sendMessage(ChatColor.GREEN + "Игрок " + username + " удален из вайтлиста!");
        } else {
            sender.sendMessage(ChatColor.RED + "Ошибка удаления игрока " + username + " из вайтлиста!");
        }
    }

    private void handleList(CommandSender sender) {
        List<String> whitelistedUsers = AuthManager.getWhitelistedUsers();

        if (whitelistedUsers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Вайтлист пуст.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Игроки в вайтлисте:");
        for (String username : whitelistedUsers) {
            sender.sendMessage(ChatColor.WHITE + "- " + username);
        }
    }
}
