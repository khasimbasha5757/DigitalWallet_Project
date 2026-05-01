package com.wallet.notification.repository;

import com.wallet.notification.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationHistory, UUID> {
    List<NotificationHistory> findAllByOrderBySentAtDesc();
}
