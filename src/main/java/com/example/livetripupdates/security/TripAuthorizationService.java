package com.example.livetripupdates.security;

import com.example.livetripupdates.domain.Cab;
import com.example.livetripupdates.domain.PermissionType;
import com.example.livetripupdates.domain.Trip;
import com.example.livetripupdates.domain.TripStatus;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.persistence.CabRepository;
import com.example.livetripupdates.persistence.TripPermissionRepository;
import com.example.livetripupdates.persistence.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TripAuthorizationService {

    private static final EnumSet<PermissionType> VIEW_PERMISSIONS =
            EnumSet.of(PermissionType.VIEW, PermissionType.ADMIN);
    private static final EnumSet<PermissionType> PUBLISH_PERMISSIONS =
            EnumSet.of(PermissionType.PUBLISH, PermissionType.ADMIN);
    private static final EnumSet<TripStatus> OPEN_TRIP_STATUSES =
            EnumSet.of(TripStatus.SCHEDULED, TripStatus.ACTIVE);

    private final TripRepository tripRepository;
    private final CabRepository cabRepository;
    private final TripPermissionRepository permissionRepository;

    public TripAuthorizationService(
            TripRepository tripRepository,
            CabRepository cabRepository,
            TripPermissionRepository permissionRepository
    ) {
        this.tripRepository = tripRepository;
        this.cabRepository = cabRepository;
        this.permissionRepository = permissionRepository;
    }

    public boolean canPublishTrip(CurrentPrincipal principal, String tripId) {
        return tripRepository.findByIdAndTenantId(tripId, principal.tenantId())
                .map(trip -> canPublish(principal, trip))
                .orElse(false);
    }

    public boolean canReplayTrip(CurrentPrincipal principal, String tripId) {
        return canSubscribeTrip(principal, tripId);
    }

    public boolean canSubscribeTopic(CurrentPrincipal principal, String rawTopic) {
        Optional<Topic> topic = Topic.parse(rawTopic);
        if (topic.isEmpty()) {
            return false;
        }
        return switch (topic.get().type()) {
            case TRIP -> canSubscribeTrip(principal, topic.get().id());
            case CAB -> canSubscribeCab(principal, topic.get().id());
            case OFFICE -> canSubscribeOffice(principal, topic.get().id());
        };
    }

    private boolean canSubscribeTrip(CurrentPrincipal principal, String tripId) {
        return tripRepository.findByIdAndTenantId(tripId, principal.tenantId())
                .map(trip -> canView(principal, trip))
                .orElse(false);
    }

    private boolean canSubscribeCab(CurrentPrincipal principal, String cabId) {
        return cabRepository.findByIdAndTenantId(cabId, principal.tenantId())
                .filter(cab -> tripRepository.existsByTenantIdAndCabIdAndStatusIn(
                        principal.tenantId(),
                        cab.getId(),
                        OPEN_TRIP_STATUSES
                ))
                .map(cab -> principal.hasRole(UserRole.TRANSPORT_ADMIN) || isAssignedDriver(principal, cab))
                .orElse(false);
    }

    private boolean canSubscribeOffice(CurrentPrincipal principal, String officeId) {
        return principal.hasRole(UserRole.TRANSPORT_ADMIN)
                && officeId != null
                && !officeId.isBlank();
    }

    private boolean canPublish(CurrentPrincipal principal, Trip trip) {
        if (principal.hasRole(UserRole.TRANSPORT_ADMIN)) {
            return true;
        }
        if (isAssignedDriver(principal, trip.getCab())) {
            return true;
        }
        return permissionRepository.existsByTripIdAndUserIdAndPermissionIn(
                trip.getId(),
                principal.userId(),
                PUBLISH_PERMISSIONS
        );
    }

    private boolean canView(CurrentPrincipal principal, Trip trip) {
        if (principal.hasRole(UserRole.TRANSPORT_ADMIN)) {
            return true;
        }
        if (isAssignedDriver(principal, trip.getCab())) {
            return true;
        }
        if (trip.getEmployee() != null && principal.userId().equals(trip.getEmployee().getId())) {
            return true;
        }
        return permissionRepository.existsByTripIdAndUserIdAndPermissionIn(
                trip.getId(),
                principal.userId(),
                VIEW_PERMISSIONS
        );
    }

    private boolean isAssignedDriver(CurrentPrincipal principal, Cab cab) {
        return principal.hasRole(UserRole.DRIVER)
                && cab.getDriver() != null
                && principal.userId().equals(cab.getDriver().getId());
    }
}
