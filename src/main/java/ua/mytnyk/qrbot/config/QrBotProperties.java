package ua.mytnyk.qrbot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("telegram")
public class QrBotProperties {
    private String botUsername;
    private long contentChannelId;
    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    public long getContentChannelId() {
        return contentChannelId;
    }

    public void setContentChannelId(long contentChannelId) {
        this.contentChannelId = contentChannelId;
    }

    @PostConstruct
    void validate() {
        if (botUsername == null || botUsername.isBlank()) {
            throw new IllegalStateException("telegram.bot-username must be configured");
        }
        if (contentChannelId == 0) {
            throw new IllegalStateException("telegram.content-channel-id must be configured");
        }
    }
}
