package ua.mytnyk.qrbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.QrAccess;

public interface QrAccessRepository extends MongoRepository<QrAccess, String> {
}
