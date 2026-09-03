package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("bot_users")
public record BotUser(@Id Long id, String username, State state, QrType selectedType,
                      Integer channelMessageId, String pendingQrId, QrListPreferences listPreferences,
                      Integer navigationMessageId, Instant updatedAt, List<Integer> pendingMessageIds,
                      String pendingMediaGroupId, List<Integer> displayedMessageIds,
                      List<QrContentItem> pendingContentItems, PendingPasswordOptions pendingPasswordOptions) {
    public BotUser withPendingContentItems(List<QrContentItem> items) {
        return new BotUser(id, username, state, selectedType, channelMessageId, pendingQrId, listPreferences,
                navigationMessageId, updatedAt, pendingMessageIds, pendingMediaGroupId, displayedMessageIds, items,
                pendingPasswordOptions);
    }
    public enum State {
        IDLE,
        WAITING_FOR_CONTENT,
        WAITING_FOR_CREATION_PASSWORD,
        WAITING_FOR_CREATION_CASE_CHOICE,
        WAITING_FOR_OPEN_PASSWORD,
        WAITING_FOR_PASSWORD_CHANGE,
        WAITING_FOR_CHANGE_CASE_CHOICE,
        WAITING_FOR_REDEEM_CONFIRMATION,
        WAITING_FOR_FEEDBACK
    }
}
