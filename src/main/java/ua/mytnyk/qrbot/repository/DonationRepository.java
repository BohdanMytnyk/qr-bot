package ua.mytnyk.qrbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.mytnyk.qrbot.domain.Donation;

public interface DonationRepository extends MongoRepository<Donation, String> {
}
