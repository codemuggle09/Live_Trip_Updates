package com.example.livetripupdates.persistence;

import com.example.livetripupdates.domain.PermissionType;
import com.example.livetripupdates.domain.TripPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface TripPermissionRepository extends JpaRepository<TripPermission, Long> {

    boolean existsByTripIdAndUserIdAndPermissionIn(
            String tripId,
            String userId,
            Collection<PermissionType> permissions
    );
}
