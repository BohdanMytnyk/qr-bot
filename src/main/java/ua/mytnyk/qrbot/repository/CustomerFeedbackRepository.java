package ua.mytnyk.qrbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.CustomerFeedback;

public interface CustomerFeedbackRepository extends MongoRepository<CustomerFeedback, String> {
}
