package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("qr_accesses")
public record QrAccess(@Id String id, @Indexed String qrId, long userId, String username, Instant openedAt) {
}
