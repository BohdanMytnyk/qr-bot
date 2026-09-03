package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("customer_feedback")
public record CustomerFeedback(@Id String id, long customerId, String username, String text, Instant createdAt) {
}
