package com.wallet.notification.controller;

import com.wallet.notification.entity.NotificationHistory;
import com.wallet.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository repository;

    @GetMapping
    public ResponseEntity<List<NotificationHistory>> getAll() {
        return ResponseEntity.ok(repository.findAll()); // Simple admin view
    }
}
