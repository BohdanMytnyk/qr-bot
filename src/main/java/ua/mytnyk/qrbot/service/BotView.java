package ua.mytnyk.qrbot.service;

import ua.mytnyk.telegram.common.model.common.api.markup.keyboard.inline.InlineKeyboard;

public record BotView(String text, InlineKeyboard keyboard) {
}
