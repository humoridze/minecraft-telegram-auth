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
import org.bukkit.entity.Player;
import ru.codexdev.telegramAuth.AuthManager;

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
