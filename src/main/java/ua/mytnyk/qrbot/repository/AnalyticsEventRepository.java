package ua.mytnyk.qrbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.AnalyticsEvent;

public interface AnalyticsEventRepository extends MongoRepository<AnalyticsEvent, String> {
}
