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

package ru.codexdev.telegramAuth.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.codexdev.telegramAuth.AuthManager;

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
