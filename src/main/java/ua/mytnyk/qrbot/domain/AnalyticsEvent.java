package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("analytics_events")
public record AnalyticsEvent(@Id String id, AnalyticsAction action, @Indexed Long actorId,
                             long chatId, @Indexed String qrId, QrType qrType,
                             Instant occurredAt, Map<String, String> attributes) {
}
