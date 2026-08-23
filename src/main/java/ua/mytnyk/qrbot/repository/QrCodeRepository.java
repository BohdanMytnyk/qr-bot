package ua.mytnyk.qrbot.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.QrCode;
import ua.mytnyk.qrbot.domain.QrStatus;

public interface QrCodeRepository extends MongoRepository<QrCode, String> {
    Optional<QrCode> findByToken(String token);
    Optional<QrCode> findByIdAndOwnerId(String id, long ownerId);
    List<QrCode> findTop10ByOwnerIdOrderByCreatedAtDesc(long ownerId);
    long countByOwnerIdAndStatus(long ownerId, QrStatus status);
}
