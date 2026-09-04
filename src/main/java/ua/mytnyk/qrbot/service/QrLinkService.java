package ua.mytnyk.qrbot.service;

import java.security.SecureRandom;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import ua.mytnyk.qrbot.config.QrBotProperties;
import ua.mytnyk.qrbot.config.QrLinkProperties;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.repository.QrCodeRepository;

@Service
public class QrLinkService {
    static final int TOKEN_LENGTH = 8;
    private static final String TOKEN_INDEX_NAME = "qr_token_unique";
    private static final int MAX_GENERATION_ATTEMPTS = 32;
    private static final char[] TOKEN_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final QrCodeRepository qrCodes;
    private final QrBotProperties telegramProperties;
    private final QrLinkProperties linkProperties;
    private final SecureRandom random;

    @Autowired
    public QrLinkService(QrCodeRepository qrCodes, QrBotProperties telegramProperties,
                         QrLinkProperties linkProperties) {
        this(qrCodes, telegramProperties, linkProperties, new SecureRandom());
    }

    QrLinkService(QrCodeRepository qrCodes, QrBotProperties telegramProperties,
                  QrLinkProperties linkProperties, SecureRandom random) {
        this.qrCodes = qrCodes;
        this.telegramProperties = telegramProperties;
        this.linkProperties = linkProperties;
        this.random = random;
    }

    public QrCode insertWithToken(Function<String, QrCode> qrFactory) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                return qrCodes.insert(qrFactory.apply(randomToken()));
            } catch (DuplicateKeyException collision) {
                if (!isTokenCollision(collision)) {
                    throw collision;
                }
                // Generate another candidate. MongoDB's unique index arbitrates concurrent inserts.
            }
        }
        throw exhaustedTokenSpace();
    }

    public String publicLink(QrCode qrCode) {
        var baseUrl = linkProperties.getPublicBaseUrl().replaceFirst("/+$", "");
        return baseUrl + "/" + qrCode.token();
    }

    public String telegramDeepLink(QrCode qrCode) {
        var username = telegramProperties.getBotUsername().replaceFirst("^@", "");
        return "https://t.me/" + username + "?start=" + qrCode.id();
    }

    public boolean includeTelegramDeepLink() {
        return linkProperties.isIncludeTelegramDeepLink();
    }

    private String randomToken() {
        var result = new char[TOKEN_LENGTH];
        for (int index = 0; index < result.length; index++) {
            result[index] = TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)];
        }
        return new String(result);
    }

    private IllegalStateException exhaustedTokenSpace() {
        return new IllegalStateException("Could not allocate a unique QR short token");
    }

    private boolean isTokenCollision(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(TOKEN_INDEX_NAME)) {
                return true;
            }
        }
        return false;
    }
}
