package ua.mytnyk.qrbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("qr.links")
public class QrLinkProperties {
    private String publicBaseUrl = "https://qr.twob.cc";
    private boolean includeTelegramDeepLink;

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isIncludeTelegramDeepLink() {
        return includeTelegramDeepLink;
    }

    public void setIncludeTelegramDeepLink(boolean includeTelegramDeepLink) {
        this.includeTelegramDeepLink = includeTelegramDeepLink;
    }
}
