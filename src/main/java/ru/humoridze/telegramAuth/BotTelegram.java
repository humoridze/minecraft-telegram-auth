/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ru.humoridze.telegramAuth.AuthManager;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BotTelegram extends TelegramLongPollingBot {
    private String username = "changeme";
    private String token = "changeme";
    private static Map<String, String> nextStep = new HashMap<>();
    private static Map<String, String> playerUsername = new HashMap<>();
    private Map<String, String> sendMessageData = new HashMap<>();
    public static Map<String, String> curentplayer = new HashMap<>();
    private final java.util.Set<Integer> processedKicks = new java.util.HashSet<>();


    public BotTelegram() {
        YamlConfiguration config = new YamlConfiguration();
        File file = new File("plugins/telegramAuth/config.yml");
        file.getParentFile().mkdirs();

        if (!file.exists()) {
            config.set("username", username);
            config.set("token", token);
            try {
                config.save(file);
            } catch (Exception e) {
                System.out.println("Error creating config file: " + e);
            }
        } else {
            try {
                config.load(file);
            } catch (IOException e) {
                System.out.println("Error loading config file: " + e);
            } catch (InvalidConfigurationException e) {
                System.out.println("Error loading config file: " + e);
            }
            username = config.getString("username");
            token = config.getString("token");
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().getText().toString().startsWith("#")){
                User user = User.getCurrentUser(update.getMessage().getChatId());
                String message = update.getMessage().getText().toString().replace("#", "");
                if (user == null){
                    this.sendMessage(update.getMessage().getChatId(), "Привяжите телеграм к аккаунту");
                }else {
                    if (user.player != null){
                        Handler.sendMCmessage(user.playername, message);
                    }else{
                        if (user.chatid != null && TelegramAuth.bot != null) {
                            TelegramAuth.bot.sendMessage(user.chatid, "Ваш аккаунт не в игре");
                        }
                    }


                }
                this.deleteMessage(update.getMessage());
            }
            if (update.getMessage().getText().toString().startsWith("/")) {
                if (update.getMessage().getText().toString().equals("/start")) {
                    sendWelcomeMessage(update.getMessage().getChatId());
                }
                if (update.getMessage().getText().toString().equals("/kick")) {
                    User user = User.getOnlineUser(update.getMessage().getChatId());
                    if (user != null) {
                        user.kick();
                        if (user.chatid != null && TelegramAuth.bot != null) {
                            TelegramAuth.bot.sendMessage(user.chatid, "Вы успешно кикнули себя с сервера");
                        }
                    } else this.sendMessage(update.getMessage().getChatId(), "Ваш аккаунт не в игре");
                }
                this.deleteMessage(update.getMessage());

            }
            else {
                if (nextStep.containsKey(update.getMessage().getChatId().toString())) {
                    String currentStep = nextStep.get(update.getMessage().getChatId().toString());

                    if (currentStep != null && currentStep.equals("asknickname")) {
                        handleNicknameInput(update.getMessage());
                    }
                    if (currentStep != null && currentStep.equals("askpassword")) {
                        handlePasswordInput(update.getMessage());
                    }
                    if(currentStep != null && currentStep.equals("none")) {
                        nextStep.remove(update.getMessage().getChatId().toString());
                    }
                }

            }
        }
        if (update.hasCallbackQuery()) {
            Integer callbackMsgId = update.getCallbackQuery().getMessage().getMessageId();
            // processedKicks теперь используется только для notme/no, а не для ys
            if (update.getCallbackQuery().getData().toString().startsWith("ys")) {
                String playername = update.getCallbackQuery().getData().toString().replace("ys", "");
                AuthManager.confirmLogin(playername);
                this.deleteMessage(update.getCallbackQuery().getMessage());
                // Получаем IP игрока
                String ip = AuthManager.getLastIp(playername);
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                String time = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").format(java.time.LocalDateTime.now());
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                btn.setText("❌ Это был не я");
                btn.setCallbackData("notme" + playername);
                row.add(btn);
                java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> keyboard = new java.util.ArrayList<>();
                keyboard.add(row);
                markup.setKeyboard(keyboard);
                org.telegram.telegrambots.meta.api.methods.send.SendMessage msg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
                msg.setChatId(chatId);
                String spoilerIp = ip != null ? "<tg-spoiler>" + escapeHtml(ip) + "</tg-spoiler>" : "";
                msg.setText("✅ Успешный вход\n" + "🕒 Время входа: " + time + (ip != null ? "\n🌐 IP: " + spoilerIp : ""));
                msg.setReplyMarkup(markup);
                msg.enableHtml(true);
                try {
                    this.execute(msg);
                } catch (TelegramApiException e) {
                    System.out.println("Error sending success message: " + e);
                }
            }
            // Обработка кнопок "Это был не я" и "Нет" — только одно сообщение о кике
            if (update.getCallbackQuery().getData().toString().startsWith("notme") || update.getCallbackQuery().getData().toString().startsWith("no")) {
                String playername;
                if (update.getCallbackQuery().getData().toString().startsWith("notme")) {
                    playername = update.getCallbackQuery().getData().toString().replace("notme", "");
                } else {
                    playername = update.getCallbackQuery().getData().toString().replace("no", "");
                }
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                
                // Проверяем, не отправляли ли уже сообщение о кике для этого сообщения
                synchronized (processedKicks) {
                    if (processedKicks.contains(update.getCallbackQuery().getMessage().getMessageId())) {
                        return; // Уже обработано
                    }
                    processedKicks.add(update.getCallbackQuery().getMessage().getMessageId());
                }
                
                // Проверяем, это сообщение о смене пароля или о входе
                String messageText = update.getCallbackQuery().getMessage().getText();
                if (messageText != null && messageText.contains("Ваш пароль был изменен в игре")) {
                    // Это сообщение о смене пароля - кикаем игрока и генерируем новый пароль
                    String newPassword = AuthManager.kickAndChangePassword(playername);
                    if (newPassword != null) {
                        String spoilerPassword = "<tg-spoiler>" + escapeHtml(newPassword) + "</tg-spoiler>";
                        SendMessage passwordMessage = new SendMessage();
                        passwordMessage.setChatId(chatId);
                        passwordMessage.setText("🔐 Новый пароль: " + spoilerPassword + "\n" +
                                              "Используйте его для входа в игру.");
                        passwordMessage.enableHtml(true);
                        try {
                            execute(passwordMessage);
                        } catch (TelegramApiException e) {
                            System.out.println("Error sending new password message: " + e);
                        }
                    } else {
                        this.sendMessage(chatId, "❌ Ошибка при генерации нового пароля.");
                    }
                } else {
                    // Это сообщение о входе - кикаем игрока
                    this.deleteMessage(update.getCallbackQuery().getMessage());
                    sendKickedMessage(playername, chatId);
                }
            }
            if (update.getCallbackQuery().getData().toString().equals("continue_registration")) {
                sendNicknameRequest(update.getCallbackQuery().getMessage().getChatId());
                this.deleteMessage(update.getCallbackQuery().getMessage());
            }
            if (update.getCallbackQuery().getData().toString().startsWith("acc")) {
                String playername = update.getCallbackQuery().getData().toString().replace("acc", "");
                curentplayer.put(update.getCallbackQuery().getMessage().getChatId().toString(), playername);
                this.sendMessage(update.getCallbackQuery().getMessage().getChatId(), "Выбран игрок " + playername);
            }
            // Убираем обработку кнопки show_password_ так как кнопка больше не нужна
        }
    }

    public void sendMessage(Long Chatid, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(Chatid);
        sendMessage.setText(message);
        sendMessage.enableHtml(true); // Включаем поддержку HTML форматирования
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("Error sending message: " + e);
        }
    }

    public void deleteMessage(Message message) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(message.getChatId());
        deleteMessage.setMessageId(message.getMessageId());
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            System.out.println("Error deleting message: " + e);
        }
    }

    public void sendPasswordChangeNotification(Long chatId, String username) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        
        InlineKeyboardButton changePasswordBtn = new InlineKeyboardButton();
        changePasswordBtn.setText("🔐 Сменить пароль");
        changePasswordBtn.setCallbackData("notme" + username);
        
        row.add(changePasswordBtn);
        
        List<List<InlineKeyboardButton>> keyboardList = new ArrayList<>();
        keyboardList.add(row);
        keyboard.setKeyboard(keyboardList);
        
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("🔐 Ваш пароль был изменен в игре.\n" +
                      "Если это были не вы, немедленно смените пароль!");
        message.setReplyMarkup(keyboard);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Error sending password change notification: " + e);
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String dispPlayer(Long chatId){
        User user = User.getUser(curentplayer.get(chatId.toString()));
        return user.player.getName();
    }

    private void handleWhitelistCommand(Message message) {
        Long chatId = message.getChatId();
        String[] args = message.getText().split(" ");

        if (args.length < 2) {
            this.sendMessage(chatId, "Использование: /whitelist <add/remove/list> [игрок]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 3) {
                    this.sendMessage(chatId, "Использование: /whitelist add <игрок>");
                    return;
                }
                handleWhitelistAdd(chatId, args[2]);
                break;

            case "remove":
                if (args.length < 3) {
                    this.sendMessage(chatId, "Использование: /whitelist remove <игрок>");
                    return;
                }
                handleWhitelistRemove(chatId, args[2]);
                break;

            case "list":
                handleWhitelistList(chatId);
                break;

            default:
                this.sendMessage(chatId, "Неизвестная команда. Используйте: add, remove, list");
                break;
        }
    }

    private void handleWhitelistAdd(Long chatId, String username) {
        if (!AuthManager.isUserRegistered(username)) {
            this.sendMessage(chatId, "Игрок " + username + " не зарегистрирован!");
            return;
        }

        if (AuthManager.isUserWhitelisted(username)) {
            this.sendMessage(chatId, "Игрок " + username + " уже в вайтлисте!");
            return;
        }

        if (AuthManager.addToWhitelist(username)) {
            this.sendMessage(chatId, "Игрок " + username + " добавлен в вайтлист!");
        } else {
            this.sendMessage(chatId, "Ошибка добавления игрока " + username + " в вайтлист!");
        }
    }

    private void handleWhitelistRemove(Long chatId, String username) {
        if (!AuthManager.isUserWhitelisted(username)) {
            this.sendMessage(chatId, "Игрок " + username + " не в вайтлисте!");
            return;
        }

        if (AuthManager.removeFromWhitelist(username)) {
            this.sendMessage(chatId, "Игрок " + username + " удален из вайтлиста!");
        } else {
            this.sendMessage(chatId, "Ошибка удаления игрока " + username + " из вайтлиста!");
        }
    }

    private void handleWhitelistList(Long chatId) {
        List<String> whitelistedUsers = AuthManager.getWhitelistedUsers();

        if (whitelistedUsers.isEmpty()) {
            this.sendMessage(chatId, "Вайтлист пуст.");
            return;
        }

        StringBuilder message = new StringBuilder("Игроки в вайтлисте:\n");
        for (String username : whitelistedUsers) {
            message.append("✅ ").append(username).append("\n");
        }

        this.sendMessage(chatId, message.toString());
    }

    // Новые методы для обновленного диалога регистрации
    private void sendWelcomeMessage(Long chatId) {
        // Проверяем, есть ли уже зарегистрированный аккаунт для этого Telegram
        String existingUsername = null;

        for (String registeredUser : AuthManager.getRegisteredUsers()) {
            Long registeredChatId = AuthManager.getTelegramChatId(registeredUser);
            if (registeredChatId != null && registeredChatId.equals(chatId)) {
                existingUsername = registeredUser;
                break;
            }
        }

        if (existingUsername != null) {
            // Получаем пароль для показа в спойлере
            String password = AuthManager.getUserPasswordForDisplay(existingUsername);

            // Показываем информацию о существующем аккаунте со спойлером
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("📋 <b>Информация:</b>\n" +
                    "👤 <b>Игрок:</b> " + escapeHtml(existingUsername) + "\n" +
                    "✅ <b>Статус:</b> Добавлен в вайтлист\n\n" +
                    "🌐 <b>Наш IP:</b> minecraft.webcodewizard.ru:25565\n" +
                    "🎮 Приятной игры!");
            message.enableHtml(true);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                System.out.println("Error sending existing account info: " + e);
            }
            return;
        }

        // Если аккаунта нет, показываем форму регистрации
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardList = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton continueButton = new InlineKeyboardButton();
        continueButton.setText("Продолжить");
        continueButton.setCallbackData("continue_registration");
        row.add(continueButton);
        keyboardList.add(row);

        keyboard.setKeyboard(keyboardList);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("🎮 Здравствуйте! Для входа на сервер вы должны зарегистрироваться в боте!");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Error sending welcome message: " + e);
        }
    }

    // Для хранения ID сообщений, которые нужно удалить
    private Map<String, Integer> nicknameRequestMessages = new HashMap<>();
    private Map<String, Integer> passwordRequestMessages = new HashMap<>();

    private void sendNicknameRequest(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("👤 Введите ваш никнейм:");

        try {
            Message sentMessage = execute(message);
            // Сохраняем ID сообщения для удаления после получения ответа
            nicknameRequestMessages.put(chatId.toString(), sentMessage.getMessageId());

            nextStep.put(chatId.toString(), "asknickname");
        } catch (TelegramApiException e) {
            System.out.println("Error sending nickname request: " + e);
        }
    }

    private void handleNicknameInput(Message message) {
        String username = message.getText().toString();
        Long chatId = message.getChatId();

        // Удаляем сообщение с запросом никнейма после получения ответа
        Integer nicknameRequestMessageId = nicknameRequestMessages.remove(chatId.toString());
        if (nicknameRequestMessageId != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId);
                deleteMessage.setMessageId(nicknameRequestMessageId);
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                System.out.println("Error deleting nickname request message: " + e);
            }
        }

        // Проверяем, не зарегистрирован ли уже пользователь
        if (AuthManager.isUserRegistered(username)) {
            this.sendMessage(chatId, "❌ Игрок " + username + " уже зарегистрирован!");
            nextStep.remove(chatId.toString());
            this.deleteMessage(message);
            return;
        }

        // Проверяем, не привязан ли уже этот Telegram к другому аккаунту
        for (String registeredUser : AuthManager.getRegisteredUsers()) {
            Long registeredChatId = AuthManager.getTelegramChatId(registeredUser);
            if (registeredChatId != null && registeredChatId.equals(chatId)) {
                this.sendMessage(chatId, "❌ Этот Telegram уже привязан к игроку " + registeredUser);
                nextStep.remove(chatId.toString());
                this.deleteMessage(message);
                return;
            }
        }

        // Сохраняем никнейм и запрашиваем пароль
        playerUsername.put(chatId.toString(), username);

        SendMessage passwordMessage = new SendMessage();
        passwordMessage.setChatId(chatId);
        passwordMessage.setText("🔐 Придумайте пароль:\n\n⚠️ Пароль будет скрыт после ввода");

        try {
            Message sentPasswordMessage = execute(passwordMessage);
            // Сохраняем ID сообщения для удаления после получения ответа
            passwordRequestMessages.put(chatId.toString(), sentPasswordMessage.getMessageId());
        } catch (TelegramApiException e) {
            System.out.println("Error sending password request: " + e);
        }

        nextStep.put(chatId.toString(), "askpassword");
        this.deleteMessage(message);
    }

    private void handlePasswordInput(Message message) {
        String password = message.getText().toString().replace(" ", "").replace("\n", "");
        Long chatId = message.getChatId();

        // Удаляем сообщение с запросом пароля после получения ответа
        Integer passwordRequestMessageId = passwordRequestMessages.remove(chatId.toString());
        if (passwordRequestMessageId != null) {
            try {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId);
                deleteMessage.setMessageId(passwordRequestMessageId);
                execute(deleteMessage);
            } catch (TelegramApiException e) {
                System.out.println("Error deleting password request message: " + e);
            }
        }

        // Проверяем длину пароля
        if (password.length() < 6) {
            this.sendMessage(chatId, "❌ Пароль должен содержать минимум 6 символов. Попробуйте еще раз:");
            this.deleteMessage(message);
            return;
        }

        String username = playerUsername.get(chatId.toString());
        if (username == null) {
            this.sendMessage(chatId, "❌ Ошибка: не найден никнейм игрока. Начните регистрацию заново с команды /start");
            nextStep.remove(chatId.toString());
            this.deleteMessage(message);
            return;
        }

        // Регистрируем пользователя и добавляем в вайтлист
        if (AuthManager.registerUser(username, password, chatId)) {
            AuthManager.addToWhitelist(username);

            SendMessage successMessage = new SendMessage();
            successMessage.setChatId(chatId);
            successMessage.setText("🎉 <b>Отлично! Вы успешно зарегистрированы и можете зайти на сервер.</b>\n\n" +
                    "📋 <b>Информация:</b>\n" +
                    "👤 <b>Игрок:</b> " + escapeHtml(username) + "\n" +
                    "🔐 <b>Пароль:</b> <tg-spoiler>" + escapeHtml(password) + "</tg-spoiler>\n" +
                    "✅ <b>Статус:</b> Добавлен в вайтлист\n\n" +
                    "🌐 <b>Наш IP:</b> minecraft.webcodewizard.ru:25565\n" +
                    "🎮 Приятной игры!");
            successMessage.enableHtml(true);

            try {
                execute(successMessage);
            } catch (TelegramApiException e) {
                System.out.println("Error sending registration success: " + e);
            }
        } else {
            this.sendMessage(chatId, "❌ Ошибка регистрации. Попробуйте еще раз.");
        }

        // Очищаем данные
        nextStep.remove(chatId.toString());
        playerUsername.remove(chatId.toString());
        this.deleteMessage(message);
    }

    private void sendKickedMessage(String playername, Long chatId) {
        String newPassword = AuthManager.kickAndChangePassword(playername);
        String spoiler = "<tg-spoiler>" + escapeHtml(newPassword) + "</tg-spoiler>";
        String text = "❌ Ваш аккаунт был кикнут с сервера\n🔑 Пароль сменен на " + spoiler;
        org.telegram.telegrambots.meta.api.methods.send.SendMessage msg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.enableHtml(true);
        try {
            this.execute(msg);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            System.out.println("Error sending kicked message: " + e);
        }
    }
}