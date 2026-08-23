package ua.mytnyk.qrbot.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    public PasswordValue hash(String password) {
        var salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordValue(salt, derive(password, salt));
    }

    public boolean matches(String password, byte[] salt, byte[] expectedHash) {
        if (salt == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash, derive(password, salt));
    }

    private byte[] derive(String password, byte[] salt) {
        var specification = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            specification.clearPassword();
        }
    }

    public record PasswordValue(byte[] salt, byte[] hash) {
    }
}
