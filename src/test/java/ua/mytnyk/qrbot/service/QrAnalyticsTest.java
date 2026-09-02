package ua.mytnyk.qrbot.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.mytnyk.analyticscommon.AnalyticsFacade;
import ua.mytnyk.qrbot.domain.AnalyticsAction;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrStatus;
import ua.mytnyk.qrbot.domain.QrType;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QrAnalyticsTest {
    @Test
    void translatesQrDomainEventIntoCommonAnalyticsEvent() {
        AnalyticsFacade facade = mock(AnalyticsFacade.class);
        var analytics = new QrAnalytics(facade);
        var qr = new QrCode("qr-1", "token", QrType.COUPON, QrStatus.ACTIVE, 1L, 2L, 3,
                null, null, Instant.EPOCH, 0, List.of(3), List.of(), null, null, null,
                null, false, null);
        analytics.track(AnalyticsAction.QR_CREATED, 1L, 2L, qr, Map.of("source", "test"));
        verify(facade).track("QR_CREATED", 1L, 2L, "qr-1", "COUPON", Map.of("source", "test"));
    }

    @Test
    void supportsEventsWithoutQrSubjectAndDefaultAttributes() {
        AnalyticsFacade facade = mock(AnalyticsFacade.class);
        var analytics = new QrAnalytics(facade);
        analytics.track(AnalyticsAction.MAIN_MENU_VIEWED, 1L, 2L);
        verify(facade).track("MAIN_MENU_VIEWED", 1L, 2L, null, null, Map.of());
    }
}
