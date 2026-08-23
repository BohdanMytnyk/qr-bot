package ua.mytnyk.qrbot.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ua.mytnyk.qrbot.domain.AnalyticsAction;
import ua.mytnyk.qrbot.domain.AnalyticsEvent;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.repository.AnalyticsEventRepository;

@Service
public class AnalyticsService {
    private final AnalyticsEventRepository events;
    private final Clock clock = Clock.systemUTC();

    public AnalyticsService(AnalyticsEventRepository events) {
        this.events = events;
    }

    public void track(AnalyticsAction action, Long actorId, long chatId) {
        track(action, actorId, chatId, null, Map.of());
    }

    public void track(AnalyticsAction action, Long actorId, long chatId, QrCode qrCode) {
        track(action, actorId, chatId, qrCode, Map.of());
    }

    public void track(AnalyticsAction action, Long actorId, long chatId, QrCode qrCode,
                      Map<String, String> attributes) {
        var event = new AnalyticsEvent(UUID.randomUUID().toString(), action, actorId, chatId,
                qrCode == null ? null : qrCode.id(), qrCode == null ? null : qrCode.type(),
                clock.instant(), Map.copyOf(attributes));
        events.insert(event);
    }
}
