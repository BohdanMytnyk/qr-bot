package ua.mytnyk.qrbot.domain;

public record PendingPasswordOptions(byte[] exactSalt, byte[] exactHash,
                                     byte[] normalizedSalt, byte[] normalizedHash) {
}
