package ua.mytnyk.qrbot.domain;

import java.util.EnumSet;
import java.util.Set;

public record QrListPreferences(Set<QrType> types, Set<QrStatus> statuses, QrListSort sort) {
    public static QrListPreferences defaults() {
        return new QrListPreferences(EnumSet.allOf(QrType.class),
                EnumSet.of(QrStatus.ACTIVE, QrStatus.REDEEMED), QrListSort.NEWEST);
    }
}
