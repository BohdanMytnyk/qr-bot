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
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@RestController
public class QrRedirectController {
    private static final Pattern SHORT_TOKEN = Pattern.compile("[A-Za-z0-9]{8}");
    private final QrCodeRepository qrCodes;
    private final QrLinkService links;
    private final MongoTemplate mongo;

    public QrRedirectController(QrCodeRepository qrCodes, QrLinkService links, MongoTemplate mongo) {
        this.qrCodes = qrCodes;
        this.links = links;
        this.mongo = mongo;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Void> redirect(@PathVariable String token) {
        if (!SHORT_TOKEN.matcher(token).matches()) {
            return ResponseEntity.notFound().build();
        }
        return qrCodes.findByToken(token)
                .filter(qrCode -> qrCode.type() != QrType.CLASSIC || qrCode.status() == QrStatus.ACTIVE)
                .<ResponseEntity<Void>>map(qrCode -> {
                    final String destination;
                    if (qrCode.type() == QrType.CLASSIC && qrCode.targetUrl() != null) {
                        destination = qrCode.targetUrl();
                        mongo.updateFirst(Query.query(Criteria.where("id").is(qrCode.id())
                                        .and("status").is(QrStatus.ACTIVE)),
                                new Update().inc("openCount", 1), ua.mytnyk.qrbot.domain.QrCode.class);
                    } else if (qrCode.type() == QrType.CLASSIC) {
                        return ResponseEntity.<Void>notFound().build();
                    } else {
                        destination = links.telegramDeepLink(qrCode);
                    }
                    return ResponseEntity.status(302)
                        .location(URI.create(destination))
                        .cacheControl(CacheControl.noStore())
                        .<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
