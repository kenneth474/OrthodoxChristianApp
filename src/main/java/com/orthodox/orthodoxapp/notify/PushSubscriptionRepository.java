package com.orthodox.orthodoxapp.notify;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Loads and saves PushSubscription rows (the devices with notifications on). */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserId(Long userId);

    long countByUserId(Long userId);

    // @Query: a query written by hand (in JPQL, SQL's Java cousin), because
    // "every account that has at least one device" isn't a method name.
    @Query("select distinct s.userId from PushSubscription s")
    List<Long> findUserIdsWithDevices();

    @Transactional
    void deleteByEndpoint(String endpoint);
}
