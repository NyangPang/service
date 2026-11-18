package com.commicom.itda.domain.notification.repository;

import com.commicom.itda.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.isRead = true where n.recipientId = :recipientId and n.isRead = false")
    int markAllRead(@Param("recipientId") Long recipientId);

    void deleteByRecipientId(Long recipientId);
}
