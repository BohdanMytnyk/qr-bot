package ua.mytnyk.qrbot.domain;

import java.util.EnumSet;
import java.util.Set;

public record QrListPreferences(Set<QrType> types, QrStatus status, QrListSort sort) {
    public static QrListPreferences defaults() {
        return new QrListPreferences(EnumSet.allOf(QrType.class), null, QrListSort.NEWEST);
    }
}
