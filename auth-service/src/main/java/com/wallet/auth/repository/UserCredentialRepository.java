package com.wallet.auth.repository;

import com.wallet.auth.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByEmailIgnoreCase(String email);
    Optional<UserCredential> findByUsernameIgnoreCase(String username);
}
