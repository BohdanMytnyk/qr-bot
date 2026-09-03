package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("qr_codes")
public record QrCode(@Id String id,
                     @Indexed(name = "qr_token_unique", unique = true,
                             partialFilter = "{ 'token': { '$type': 'string' } }") String token,
                     QrType type, QrStatus status,
                     long ownerId, long channelId, int messageId, byte[] passwordSalt,
                     byte[] passwordHash, Instant createdAt, long openCount, List<Integer> messageIds,
                     List<QrContentItem> contentItems, Long redeemedByUserId, String redeemedByUsername,
                     String redeemedByName, Instant redeemedAt, Boolean ignorePasswordCase, String previewText,
                     String targetUrl) {

    public QrCode(String id, String token, QrType type, QrStatus status, long ownerId, long channelId,
                  int messageId, byte[] passwordSalt, byte[] passwordHash, Instant createdAt, long openCount,
                  List<Integer> messageIds, List<QrContentItem> contentItems, Long redeemedByUserId,
                  String redeemedByUsername, String redeemedByName, Instant redeemedAt,
                  Boolean ignorePasswordCase, String previewText) {
        this(id, token, type, status, ownerId, channelId, messageId, passwordSalt, passwordHash, createdAt,
                openCount, messageIds, contentItems, redeemedByUserId, redeemedByUsername, redeemedByName,
                redeemedAt, ignorePasswordCase, previewText, null);
    }

    public List<Integer> contentMessageIds() {
        return messageIds == null || messageIds.isEmpty() ? List.of(messageId) : messageIds;
    }
}
