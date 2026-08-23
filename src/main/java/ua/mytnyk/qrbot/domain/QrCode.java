package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("qr_codes")
public record QrCode(@Id String id, String token, QrType type, QrStatus status,
                     long ownerId, long channelId, int messageId, byte[] passwordSalt,
                     byte[] passwordHash, Instant createdAt, long openCount, List<Integer> messageIds,
                     List<QrContentItem> contentItems) {

    public List<Integer> contentMessageIds() {
        return messageIds == null || messageIds.isEmpty() ? List.of(messageId) : messageIds;
    }
}
