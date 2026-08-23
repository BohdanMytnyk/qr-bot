package ua.mytnyk.qrbot.domain;

import java.util.EnumSet;
import java.util.Set;

public record QrListPreferences(Set<QrType> types, Set<QrStatus> statuses, QrListSort sort, Integer page) {
    public QrListPreferences {
        page = page == null ? 0 : page;
    }

    public static QrListPreferences defaults() {
        return new QrListPreferences(EnumSet.allOf(QrType.class),
                EnumSet.of(QrStatus.ACTIVE, QrStatus.REDEEMED), QrListSort.NEWEST, 0);
    }
}
