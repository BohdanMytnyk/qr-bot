package ua.mytnyk.qrbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.BotUser;

public interface BotUserRepository extends MongoRepository<BotUser, Long> {
}
