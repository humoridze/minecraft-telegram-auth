/*
 * Copyright (c) 2024 humoridze. All rights reserved.
 * 
 * This software is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is strictly prohibited.
 */

package ru.humoridze.telegramAuth;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BotTelegram extends TelegramLongPollingBot {
    private static final String NICKNAME_PATTERN = "^[A-Za-z0-9_]{3,16}$";

    private final String username;
    private final String token;
    private static final Map<String, String> nextStep = new ConcurrentHashMap<>();
    private static final Map<String, String> playerUsername = new ConcurrentHashMap<>();
    public static final Map<String, String> curentplayer = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> processedKicks = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> nicknameRequestMessages = new ConcurrentHashMap<>();
    private final Map<String, Integer> passwordRequestMessages = new ConcurrentHashMap<>();

    public BotTelegram(String username, String token) {
        this.username = username;
        this.token = token;
    }

    public BotTelegram() {
        String loadedUsername = "changeme";
        String loadedToken = "changeme";
        YamlConfiguration config = new YamlConfiguration();
        java.io.File file = new java.io.File("plugins/telegramAuth/config.yml");
        file.getParentFile().mkdirs();
        if (file.exists()) {
            try {
                config.load(file);
                if (config.getString("username") != null) {
                    loadedUsername = config.getString("username");
                }
                if (config.getString("token") != null) {
                    loadedToken = config.getString("token");
                }
            } catch (java.io.IOException | InvalidConfigurationException e) {
                System.out.println("Error loading config file: " + e);
            }
        }
        this.username = loadedUsername;
        this.token = loadedToken;
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
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        String text = message.getText();
        Long chatId = message.getChatId();

        if (text.startsWith("#")) {
            User user = User.getCurrentUser(chatId);
            String gameMessage = text.substring(1);
            if (user == null) {
                sendMessage(chatId, "Привяжите телеграм к аккаунту");
            } else if (user.player != null) {
                Handler.sendMCmessage(user.playername, gameMessage);
            } else {
                sendMessage(chatId, "Ваш аккаунт не в игре");
            }
            deleteMessage(message);
            return;
        }

        if (text.startsWith("/")) {
            String command = text.split(" ")[0].toLowerCase();
            int at = command.indexOf('@');
            if (at > 0) {
                command = command.substring(0, at);
            }
            if (command.equals("/start")) {
                sendWelcomeMessage(chatId);
            } else if (command.equals("/kick")) {
                User user = User.getOnlineUser(chatId);
                if (user != null) {
                    user.kick();
                    sendMessage(chatId, "Вы успешно кикнули себя с сервера");
                } else {
                    sendMessage(chatId, "Ваш аккаунт не в игре");
                }
            } else if (command.equals("/whitelist")) {
                if (TelegramAuth.getInstance() != null && TelegramAuth.getInstance().isTelegramAdmin(chatId)) {
                    handleWhitelistCommand(message);
                } else {
                    sendMessage(chatId, "Недостаточно прав.");
                }
            }
            deleteMessage(message);
            return;
        }

        String currentStep = nextStep.get(chatId.toString());
        if (currentStep == null) {
            return;
        }
        if (currentStep.equals("asknickname")) {
            handleNicknameInput(message);
        } else if (currentStep.equals("askpassword")) {
            handlePasswordInput(message);
        } else if (currentStep.equals("none")) {
            nextStep.remove(chatId.toString());
        }
    }

    private void handleCallback(CallbackQuery callback) {
        answerCallback(callback.getId());
        String data = callback.getData();
        if (data == null || callback.getMessage() == null) {
            return;
        }
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        if (data.equals("continue_registration")) {
            sendNicknameRequest(chatId);
            deleteMessage(callback.getMessage());
            return;
        }

        if (data.startsWith("acc:") || data.startsWith("acc")) {
            String playername = data.startsWith("acc:") ? data.substring(4) : data.substring(3);
            curentplayer.put(chatId.toString(), playername);
            sendMessage(chatId, "Выбран игрок " + playername);
            return;
        }

        String yesUsername = callbackUsername(data, "yes:", "ys");
        if (yesUsername != null) {
            AuthManager.confirmLogin(yesUsername);
            deleteMessage(callback.getMessage());
            return;
        }

        String denyUsername = callbackUsername(data, "notme:", "notme");
        if (denyUsername == null) {
            if (data.startsWith("no:") || (data.startsWith("no") && !data.startsWith("notme"))) {
                denyUsername = data.startsWith("no:") ? data.substring(3) : data.substring(2);
            }
        }
        if (denyUsername != null) {
            if (!processedKicks.add(messageId)) {
                return;
            }
            String messageText = callback.getMessage().getText();
            if (messageText != null && messageText.contains("Ваш пароль был изменен в игре")) {
                String newPassword = AuthManager.kickAndChangePassword(denyUsername);
                if (newPassword != null) {
                    SendMessage passwordMessage = new SendMessage();
                    passwordMessage.setChatId(chatId);
                    passwordMessage.setText("🔐 Новый пароль: <tg-spoiler>" + escapeHtml(newPassword) + "</tg-spoiler>\n"
                            + "Используйте его для входа в игру.");
                    passwordMessage.enableHtml(true);
                    try {
                        execute(passwordMessage);
                    } catch (TelegramApiException e) {
                        System.out.println("Error sending new password message: " + e);
                    }
                } else {
                    sendMessage(chatId, "❌ Ошибка при генерации нового пароля.");
                }
            } else {
                deleteMessage(callback.getMessage());
                sendKickedMessage(denyUsername, chatId);
            }
        }
    }

    private String callbackUsername(String data, String delimitedPrefix, String legacyPrefix) {
        if (data.startsWith(delimitedPrefix)) {
            return data.substring(delimitedPrefix.length());
        }
        if (data.startsWith(legacyPrefix)) {
            return data.substring(legacyPrefix.length());
        }
        return null;
    }

    public void sendSuccessLogin(Long chatId, String playername, String ip) {
        String time = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .format(java.time.LocalDateTime.now());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton denyButton = new InlineKeyboardButton();
        denyButton.setText("❌ Это был не я");
        denyButton.setCallbackData("notme:" + playername);
        row.add(denyButton);
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);
        markup.setKeyboard(keyboard);

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        String spoilerIp = ip != null ? "<tg-spoiler>" + escapeHtml(ip) + "</tg-spoiler>" : "";
        msg.setText("✅ Успешный вход\n" + "🕒 Время входа: " + time + (ip != null ? "\n🌐 IP: " + spoilerIp : ""));
        msg.setReplyMarkup(markup);
        msg.enableHtml(true);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            System.out.println("Error sending success message: " + e);
        }
    }

    private void answerCallback(String callbackId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        try {
            execute(answer);
        } catch (TelegramApiException ignored) {
        }
    }

    public void sendMessage(Long Chatid, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(Chatid);
        sendMessage.setText(message);
        sendMessage.enableHtml(true);
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
        changePasswordBtn.setCallbackData("notme:" + username);
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

    private void handleWhitelistCommand(Message message) {
        Long chatId = message.getChatId();
        String[] args = message.getText().split(" ");

        if (args.length < 2) {
            sendMessage(chatId, "Использование: /whitelist <add/remove/list> [игрок]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 3) {
                    sendMessage(chatId, "Использование: /whitelist add <игрок>");
                    return;
                }
                handleWhitelistAdd(chatId, args[2]);
                break;
            case "remove":
                if (args.length < 3) {
                    sendMessage(chatId, "Использование: /whitelist remove <игрок>");
                    return;
                }
                handleWhitelistRemove(chatId, args[2]);
                break;
            case "list":
                handleWhitelistList(chatId);
                break;
            default:
                sendMessage(chatId, "Неизвестная команда. Используйте: add, remove, list");
                break;
        }
    }

    private void handleWhitelistAdd(Long chatId, String username) {
        if (!AuthManager.isUserRegistered(username)) {
            sendMessage(chatId, "Игрок " + username + " не зарегистрирован!");
            return;
        }
        if (AuthManager.isUserWhitelisted(username)) {
            sendMessage(chatId, "Игрок " + username + " уже в вайтлисте!");
            return;
        }
        if (AuthManager.addToWhitelist(username)) {
            sendMessage(chatId, "Игрок " + username + " добавлен в вайтлист!");
        } else {
            sendMessage(chatId, "Ошибка добавления игрока " + username + " в вайтлист!");
        }
    }

    private void handleWhitelistRemove(Long chatId, String username) {
        if (!AuthManager.isUserWhitelisted(username)) {
            sendMessage(chatId, "Игрок " + username + " не в вайтлисте!");
            return;
        }
        if (AuthManager.removeFromWhitelist(username)) {
            sendMessage(chatId, "Игрок " + username + " удален из вайтлиста!");
        } else {
            sendMessage(chatId, "Ошибка удаления игрока " + username + " из вайтлиста!");
        }
    }

    private void handleWhitelistList(Long chatId) {
        List<String> whitelistedUsers = AuthManager.getWhitelistedUsers();
        if (whitelistedUsers.isEmpty()) {
            sendMessage(chatId, "Вайтлист пуст.");
            return;
        }
        StringBuilder message = new StringBuilder("Игроки в вайтлисте:\n");
        for (String nickname : whitelistedUsers) {
            message.append("✅ ").append(nickname).append("\n");
        }
        sendMessage(chatId, message.toString());
    }

    private void sendWelcomeMessage(Long chatId) {
        String existingUsername = null;
        for (String registeredUser : AuthManager.getRegisteredUsers()) {
            Long registeredChatId = AuthManager.getTelegramChatId(registeredUser);
            if (registeredChatId != null && registeredChatId.equals(chatId)) {
                existingUsername = registeredUser;
                break;
            }
        }

        String serverIp = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getServerIp()
                : "changeme";

        if (existingUsername != null) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("📋 <b>Информация:</b>\n" +
                    "👤 <b>Игрок:</b> " + escapeHtml(existingUsername) + "\n" +
                    "✅ <b>Статус:</b> Добавлен в вайтлист\n\n" +
                    "🌐 <b>Наш IP:</b> " + escapeHtml(serverIp) + "\n" +
                    "🎮 Приятной игры!");
            message.enableHtml(true);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                System.out.println("Error sending existing account info: " + e);
            }
            return;
        }

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

    private void sendNicknameRequest(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("👤 Введите ваш никнейм:");
        try {
            Message sentMessage = execute(message);
            nicknameRequestMessages.put(chatId.toString(), sentMessage.getMessageId());
            nextStep.put(chatId.toString(), "asknickname");
        } catch (TelegramApiException e) {
            System.out.println("Error sending nickname request: " + e);
        }
    }

    private void handleNicknameInput(Message message) {
        String nickname = message.getText().trim();
        Long chatId = message.getChatId();

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

        if (!nickname.matches(NICKNAME_PATTERN)) {
            sendMessage(chatId, "❌ Никнейм должен быть 3–16 символов: латиница, цифры и _");
            sendNicknameRequest(chatId);
            deleteMessage(message);
            return;
        }

        if (AuthManager.isUserRegistered(nickname)) {
            sendMessage(chatId, "❌ Игрок " + nickname + " уже зарегистрирован!");
            nextStep.remove(chatId.toString());
            deleteMessage(message);
            return;
        }

        for (String registeredUser : AuthManager.getRegisteredUsers()) {
            Long registeredChatId = AuthManager.getTelegramChatId(registeredUser);
            if (registeredChatId != null && registeredChatId.equals(chatId)) {
                sendMessage(chatId, "❌ Этот Telegram уже привязан к игроку " + registeredUser);
                nextStep.remove(chatId.toString());
                deleteMessage(message);
                return;
            }
        }

        playerUsername.put(chatId.toString(), nickname);

        SendMessage passwordMessage = new SendMessage();
        passwordMessage.setChatId(chatId);
        passwordMessage.setText("🔐 Придумайте пароль:\n\n⚠️ Пароль будет скрыт после ввода");
        try {
            Message sentPasswordMessage = execute(passwordMessage);
            passwordRequestMessages.put(chatId.toString(), sentPasswordMessage.getMessageId());
        } catch (TelegramApiException e) {
            System.out.println("Error sending password request: " + e);
        }

        nextStep.put(chatId.toString(), "askpassword");
        deleteMessage(message);
    }

    private void handlePasswordInput(Message message) {
        String password = message.getText().replace(" ", "").replace("\n", "");
        Long chatId = message.getChatId();

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

        int minLength = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getMinPasswordLength()
                : 6;
        if (password.length() < minLength) {
            sendMessage(chatId, "❌ Пароль должен содержать минимум " + minLength + " символов. Попробуйте еще раз:");
            deleteMessage(message);
            return;
        }

        String nickname = playerUsername.get(chatId.toString());
        if (nickname == null) {
            sendMessage(chatId, "❌ Ошибка: не найден никнейм игрока. Начните регистрацию заново с команды /start");
            nextStep.remove(chatId.toString());
            deleteMessage(message);
            return;
        }

        String serverIp = TelegramAuth.getInstance() != null
                ? TelegramAuth.getInstance().getServerIp()
                : "changeme";

        if (AuthManager.registerUser(nickname, password, chatId)) {
            AuthManager.addToWhitelist(nickname);
            SendMessage successMessage = new SendMessage();
            successMessage.setChatId(chatId);
            successMessage.setText("🎉 <b>Отлично! Вы успешно зарегистрированы и можете зайти на сервер.</b>\n\n" +
                    "📋 <b>Информация:</b>\n" +
                    "👤 <b>Игрок:</b> " + escapeHtml(nickname) + "\n" +
                    "🔐 <b>Пароль:</b> <tg-spoiler>" + escapeHtml(password) + "</tg-spoiler>\n" +
                    "✅ <b>Статус:</b> Добавлен в вайтлист\n\n" +
                    "🌐 <b>Наш IP:</b> " + escapeHtml(serverIp) + "\n" +
                    "🎮 Приятной игры!");
            successMessage.enableHtml(true);
            try {
                execute(successMessage);
            } catch (TelegramApiException e) {
                System.out.println("Error sending registration success: " + e);
            }
        } else {
            sendMessage(chatId, "❌ Ошибка регистрации. Попробуйте еще раз.");
        }

        nextStep.remove(chatId.toString());
        playerUsername.remove(chatId.toString());
        deleteMessage(message);
    }

    private void sendKickedMessage(String playername, Long chatId) {
        String newPassword = AuthManager.kickAndChangePassword(playername);
        if (newPassword == null) {
            sendMessage(chatId, "❌ Не удалось сменить пароль.");
            return;
        }
        String spoiler = "<tg-spoiler>" + escapeHtml(newPassword) + "</tg-spoiler>";
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("❌ Ваш аккаунт был кикнут с сервера\n🔑 Пароль сменен на " + spoiler);
        msg.enableHtml(true);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            System.out.println("Error sending kicked message: " + e);
        }
    }
}
