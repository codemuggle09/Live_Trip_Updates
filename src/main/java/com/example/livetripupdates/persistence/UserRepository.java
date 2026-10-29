package com.example.livetripupdates.persistence;

import com.example.livetripupdates.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByIdAndTenantId(String id, String tenantId);
}
