package ua.mytnyk.qrbot.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import ua.mytnyk.analyticscommon.AnalyticsFacade;
import ua.mytnyk.qrbot.domain.AnalyticsAction;
import ua.mytnyk.qrbot.domain.QrCode;

@Service
public class QrAnalytics {
    private final AnalyticsFacade analytics;

    public QrAnalytics(AnalyticsFacade analytics) {
        this.analytics = analytics;
    }

    public void track(AnalyticsAction action, Long actorId, long chatId) {
        track(action, actorId, chatId, null, Map.of());
    }

    public void track(AnalyticsAction action, Long actorId, long chatId, QrCode qrCode) {
        track(action, actorId, chatId, qrCode, Map.of());
    }

    public void track(AnalyticsAction action, Long actorId, long chatId, QrCode qrCode,
                      Map<String, String> attributes) {
        analytics.track(action.name(), actorId, chatId, qrCode == null ? null : qrCode.id(),
                qrCode == null ? null : qrCode.type().name(), attributes);
    }
}
