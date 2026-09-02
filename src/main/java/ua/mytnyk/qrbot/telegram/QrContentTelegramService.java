package ua.mytnyk.qrbot.telegram;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import ua.mytnyk.qrbot.domain.QrContentItem;
import ua.mytnyk.telegram.common.client.TelegramClient;
import ua.mytnyk.telegram.common.model.common.api.TelegramMedia;

@Component
public class QrContentTelegramService {
    private final TelegramClient telegram;

    public QrContentTelegramService(TelegramClient telegram) {
        this.telegram = telegram;
    }

    public List<Integer> sendContent(long chatId, List<QrContentItem> items) {
        var messageIds = new ArrayList<Integer>();
        int index = 0;
        while (index < items.size()) {
            QrContentItem item = items.get(index);
            if (item.kind() == QrContentItem.Kind.TEXT) {
                messageIds.add(telegram.sendText(chatId, item.text()));
                index++;
                continue;
            }
            var group = new ArrayList<TelegramMedia>();
            boolean documentGroup = item.kind() == QrContentItem.Kind.DOCUMENT;
            while (index < items.size() && group.size() < 10) {
                QrContentItem candidate = items.get(index);
                if (candidate.kind() == QrContentItem.Kind.TEXT
                        || (candidate.kind() == QrContentItem.Kind.DOCUMENT) != documentGroup) {
                    break;
                }
                group.add(toTelegramMedia(candidate));
                index++;
            }
            if (group.size() == 1) {
                messageIds.add(telegram.sendMedia(chatId, group.get(0)));
            } else {
                messageIds.addAll(telegram.sendMediaGroup(chatId, group));
            }
        }
        return messageIds;
    }

    private TelegramMedia toTelegramMedia(QrContentItem item) {
        TelegramMedia.Type type = switch (item.kind()) {
            case PHOTO -> TelegramMedia.Type.PHOTO;
            case VIDEO -> TelegramMedia.Type.VIDEO;
            case DOCUMENT -> TelegramMedia.Type.DOCUMENT;
            case TEXT -> throw new IllegalArgumentException("Text is not media");
        };
        return new TelegramMedia(type, item.fileId(), item.caption());
    }
}
