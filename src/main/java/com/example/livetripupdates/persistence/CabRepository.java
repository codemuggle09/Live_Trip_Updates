package com.example.livetripupdates.persistence;

import com.example.livetripupdates.domain.Cab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CabRepository extends JpaRepository<Cab, String> {

    Optional<Cab> findByIdAndTenantId(String id, String tenantId);
}
