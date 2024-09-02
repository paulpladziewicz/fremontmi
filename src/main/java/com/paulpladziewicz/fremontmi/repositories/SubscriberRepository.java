package com.paulpladziewicz.fremontmi.repositories;

import com.paulpladziewicz.fremontmi.models.Subscriber;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriberRepository extends MongoRepository<Subscriber, String> {
    Subscriber findByEmailIgnoreCase(String email);
}

