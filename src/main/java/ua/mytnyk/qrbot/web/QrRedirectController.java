package ua.mytnyk.qrbot.web;

import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ua.mytnyk.qrbot.repository.QrCodeRepository;
import ua.mytnyk.qrbot.service.QrLinkService;

@RestController
public class QrRedirectController {
    private static final Pattern SHORT_TOKEN = Pattern.compile("[A-Za-z0-9]{8}");
    private final QrCodeRepository qrCodes;
    private final QrLinkService links;

    public QrRedirectController(QrCodeRepository qrCodes, QrLinkService links) {
        this.qrCodes = qrCodes;
        this.links = links;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Void> redirect(@PathVariable String token) {
        if (!SHORT_TOKEN.matcher(token).matches()) {
            return ResponseEntity.notFound().build();
        }
        return qrCodes.findByToken(token)
                .map(qrCode -> ResponseEntity.status(302)
                        .location(URI.create(links.telegramDeepLink(qrCode)))
                        .cacheControl(CacheControl.noStore())
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
