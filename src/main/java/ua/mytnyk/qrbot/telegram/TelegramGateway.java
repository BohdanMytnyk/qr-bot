package ua.mytnyk.qrbot.telegram;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.config.TelegramProperties;
import ua.mytnyk.telegram.common.model.common.api.CopyMessageRequest;
import ua.mytnyk.telegram.common.model.common.api.SendMessageRequest;
import ua.mytnyk.telegram.common.model.common.api.EditMessageTextRequest;
import ua.mytnyk.telegram.common.model.common.api.AnswerCallbackQueryRequest;
import ua.mytnyk.telegram.common.model.common.api.markup.keyboard.inline.InlineKeyboard;
import ua.mytnyk.qrbot.domain.QrContentItem;

@Component
public class TelegramGateway {
    private final TelegramClient client;
    private final RestClient restClient;
    private final String botApiUrl;
    public TelegramGateway(TelegramClient client, @Qualifier("telegramRestClient") RestClient restClient,
                           TelegramProperties properties) {
        this.client = client;
        this.restClient = restClient;
        this.botApiUrl = properties.getApiBaseUrl().replaceAll("/+$", "") + "/bot" + properties.getToken();
    }

    public int sendText(long chatId, String text) {
        return client.sendMessage(SendMessageRequest.builder().chatId(chatId).text(text).build()).getMessageId();
    }

    public int sendInline(long chatId, String text, InlineKeyboard keyboard) {
        return client.sendMessage(SendMessageRequest.builder().chatId(chatId).text(text).replyMarkup(keyboard).build())
                .getMessageId();
    }

    public void editInline(long chatId, int messageId, String text, InlineKeyboard keyboard) {
        try {
            client.editMessageText(EditMessageTextRequest.builder().chatId(chatId).messageId(messageId)
                    .text(text).replyMarkup(keyboard).build());
        } catch (HttpClientErrorException.BadRequest exception) {
            if (!exception.getResponseBodyAsString().contains("message is not modified")) {
                throw exception;
            }
        }
    }

    public void answerCallback(String callbackId, String text) {
        try {
            client.answerCallbackQuery(AnswerCallbackQueryRequest.builder()
                    .callbackQueryId(callbackId).text(text).build());
        } catch (HttpClientErrorException.BadRequest exception) {
            var response = exception.getResponseBodyAsString();
            if (!response.contains("query is too old") && !response.contains("query ID is invalid")) {
                throw exception;
            }
        }
    }

    public int copyMessage(long chatId, long fromChatId, int messageId) {
        return client.copyMessage(CopyMessageRequest.builder().chatId(chatId).fromChatId(fromChatId).messageId(messageId).build());
    }

    public int copyMessage(long chatId, long fromChatId, int messageId, InlineKeyboard keyboard) {
        return client.copyMessage(CopyMessageRequest.builder().chatId(chatId).fromChatId(fromChatId)
                .messageId(messageId).replyMarkup(keyboard).build());
    }

    public List<Integer> copyMessages(long chatId, long fromChatId, List<Integer> messageIds) {
        if (messageIds.size() == 1) {
            return List.of(copyMessage(chatId, fromChatId, messageIds.get(0)));
        }
        var response = restClient.post().uri(botApiUrl + "/copyMessages")
                .body(Map.of("chat_id", chatId, "from_chat_id", fromChatId, "message_ids", messageIds))
                .retrieve().body(JsonNode.class);
        var copiedMessageIds = new ArrayList<Integer>();
        for (var message : response.path("result")) {
            copiedMessageIds.add(message.path("message_id").asInt());
        }
        return copiedMessageIds;
    }

    public void deleteMessage(long chatId, int messageId) {
        try {
            restClient.post().uri(botApiUrl + "/deleteMessage")
                    .body(Map.of("chat_id", chatId, "message_id", messageId))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest exception) {
            var response = exception.getResponseBodyAsString();
            if (!response.contains("message to delete not found") && !response.contains("message can't be deleted")) {
                throw exception;
            }
        }
    }

    public void publishCommands() {
        var commands = List.of(
                Map.of("command", "start", "description", "🏠 Menu"));
        restClient.post().uri(botApiUrl + "/setMyCommands")
                .body(Map.of("commands", commands)).retrieve().toBodilessEntity();
    }

    public int sendPhoto(long chatId, String filename, byte[] content, String caption) {
        return sendPhoto(chatId, filename, content, caption, null);
    }

    public int sendPhoto(long chatId, String filename, byte[] content, String caption, InlineKeyboard keyboard) {
        var photo = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("chat_id", Long.toString(chatId));
        body.add("caption", Objects.requireNonNullElse(caption, ""));
        body.add("photo", photo);
        if (keyboard != null) {
            try {
                body.add("reply_markup", new ObjectMapper().writeValueAsString(keyboard));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Cannot serialize inline keyboard", exception);
            }
        }
        var response = restClient.post().uri(botApiUrl + "/sendPhoto").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class);
        return response.path("result").path("message_id").asInt();
    }

    public List<Integer> sendContent(long chatId, List<QrContentItem> items) {
        var messageIds = new ArrayList<Integer>();
        var index = 0;
        while (index < items.size()) {
            var item = items.get(index);
            if (item.kind() == QrContentItem.Kind.TEXT) {
                messageIds.add(sendText(chatId, item.text()));
                index++;
                continue;
            }
            var group = new ArrayList<QrContentItem>();
            var documentGroup = item.kind() == QrContentItem.Kind.DOCUMENT;
            while (index < items.size() && group.size() < 10) {
                var candidate = items.get(index);
                if (candidate.kind() == QrContentItem.Kind.TEXT
                        || (candidate.kind() == QrContentItem.Kind.DOCUMENT) != documentGroup) {
                    break;
                }
                group.add(candidate);
                index++;
            }
            if (group.size() == 1) {
                messageIds.add(sendMedia(chatId, group.get(0)));
            } else {
                messageIds.addAll(sendMediaGroup(chatId, group));
            }
        }
        return messageIds;
    }

    private int sendMedia(long chatId, QrContentItem item) {
        var method = switch (item.kind()) {
            case PHOTO -> "sendPhoto";
            case VIDEO -> "sendVideo";
            case DOCUMENT -> "sendDocument";
            case TEXT -> throw new IllegalArgumentException("Text is not media");
        };
        var mediaField = item.kind().name().toLowerCase();
        var body = new java.util.HashMap<String, Object>();
        body.put("chat_id", chatId);
        body.put(mediaField, item.fileId());
        if (item.caption() != null) {
            body.put("caption", item.caption());
        }
        var response = restClient.post().uri(botApiUrl + "/" + method).body(body).retrieve().body(JsonNode.class);
        return response.path("result").path("message_id").asInt();
    }

    private List<Integer> sendMediaGroup(long chatId, List<QrContentItem> items) {
        var media = items.stream().map(item -> {
            var value = new java.util.HashMap<String, Object>();
            value.put("type", item.kind().name().toLowerCase());
            value.put("media", item.fileId());
            if (item.caption() != null) {
                value.put("caption", item.caption());
            }
            return value;
        }).toList();
        var response = restClient.post().uri(botApiUrl + "/sendMediaGroup")
                .body(Map.of("chat_id", chatId, "media", media)).retrieve().body(JsonNode.class);
        var messageIds = new ArrayList<Integer>();
        for (var message : response.path("result")) {
            messageIds.add(message.path("message_id").asInt());
        }
        return messageIds;
    }
}
