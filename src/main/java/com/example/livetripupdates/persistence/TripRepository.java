package com.example.livetripupdates.persistence;

import com.example.livetripupdates.domain.Trip;
import com.example.livetripupdates.domain.TripStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, String> {

    @EntityGraph(attributePaths = {"tenant", "cab", "cab.driver", "employee"})
    Optional<Trip> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndCabIdAndStatusIn(String tenantId, String cabId, Collection<TripStatus> statuses);
}
