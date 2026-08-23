package ua.mytnyk.qrbot.domain;

public record QrContentItem(Kind kind, String text, String caption, String fileId,
                            String fileUniqueId, int order) {
    public enum Kind {
        TEXT,
        PHOTO,
        VIDEO,
        DOCUMENT
    }
}
