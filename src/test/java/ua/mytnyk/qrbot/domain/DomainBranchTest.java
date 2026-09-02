package ua.mytnyk.qrbot.domain;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainBranchTest {
    @Test
    void contentMessageIdsFallBackOnlyForNullOrEmptyLegacyList() {
        assertThat(qr(null).contentMessageIds()).containsExactly(20);
        assertThat(qr(List.of()).contentMessageIds()).containsExactly(20);
        assertThat(qr(List.of(21, 22)).contentMessageIds()).containsExactly(21, 22);
    }

    @Test
    void listDefaultsOnlyReplaceMissingSets() {
        var defaults = QrListPreferences.defaults();
        assertThat(defaults.types()).containsExactlyInAnyOrder(QrType.values());
        assertThat(defaults.statuses()).containsExactlyInAnyOrder(QrStatus.ACTIVE, QrStatus.REDEEMED);
        assertThat(defaults.sort()).isEqualTo(QrListSort.NEWEST);
        assertThat(defaults.page()).isZero();
    }

    private static QrCode qr(List<Integer> ids) {
        return new QrCode("id", null, QrType.CONTENT, QrStatus.ACTIVE, 1, -100123, 20,
                null, null, Instant.EPOCH, 0, ids, null, null, null, null, null, null, null);
    }
}
