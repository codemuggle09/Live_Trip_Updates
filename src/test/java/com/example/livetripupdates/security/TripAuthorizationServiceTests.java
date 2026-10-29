package com.example.livetripupdates.security;

import com.example.livetripupdates.domain.Cab;
import com.example.livetripupdates.domain.PermissionType;
import com.example.livetripupdates.domain.Tenant;
import com.example.livetripupdates.domain.Trip;
import com.example.livetripupdates.domain.TripPermission;
import com.example.livetripupdates.domain.TripStatus;
import com.example.livetripupdates.domain.User;
import com.example.livetripupdates.domain.UserRole;
import com.example.livetripupdates.persistence.CabRepository;
import com.example.livetripupdates.persistence.TenantRepository;
import com.example.livetripupdates.persistence.TripPermissionRepository;
import com.example.livetripupdates.persistence.TripRepository;
import com.example.livetripupdates.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(TripAuthorizationService.class)
class TripAuthorizationServiceTests {

    private final TripAuthorizationService authorizationService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CabRepository cabRepository;
    private final TripRepository tripRepository;
    private final TripPermissionRepository permissionRepository;

    @Autowired
    TripAuthorizationServiceTests(
            TripAuthorizationService authorizationService,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            CabRepository cabRepository,
            TripRepository tripRepository,
            TripPermissionRepository permissionRepository
    ) {
        this.authorizationService = authorizationService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.cabRepository = cabRepository;
        this.tripRepository = tripRepository;
        this.permissionRepository = permissionRepository;
    }

    @BeforeEach
    void setUp() {
        Tenant tenantOne = tenantRepository.save(new Tenant("tenant-1", "Tenant One"));
        Tenant tenantTwo = tenantRepository.save(new Tenant("tenant-2", "Tenant Two"));

        User driver = userRepository.save(new User("driver-1", tenantOne, "driver-one", UserRole.DRIVER));
        User employee = userRepository.save(new User("employee-1", tenantOne, "employee-one", UserRole.EMPLOYEE));
        User viewer = userRepository.save(new User("viewer-1", tenantOne, "viewer-one", UserRole.EMPLOYEE));
        User otherTenantEmployee = userRepository.save(
                new User("employee-2", tenantTwo, "employee-two", UserRole.EMPLOYEE)
        );

        Cab cab = cabRepository.save(new Cab("cab-1", tenantOne, driver));
        Cab otherTenantCab = cabRepository.save(new Cab("cab-2", tenantTwo, null));

        Trip trip = tripRepository.save(new Trip(
                "trip-1",
                tenantOne,
                cab,
                employee,
                "office-1",
                TripStatus.ACTIVE
        ));
        tripRepository.save(new Trip(
                "trip-2",
                tenantTwo,
                otherTenantCab,
                otherTenantEmployee,
                "office-2",
                TripStatus.ACTIVE
        ));
        permissionRepository.save(new TripPermission(trip, viewer, PermissionType.VIEW));
    }

    @Test
    void employeeCanSubscribeOwnTrip() {
        CurrentPrincipal employee = principal("employee-1", "tenant-1", UserRole.EMPLOYEE);

        assertThat(authorizationService.canSubscribeTopic(employee, "trip:trip-1")).isTrue();
    }

    @Test
    void crossTenantSubscriptionIsRejected() {
        CurrentPrincipal employee = principal("employee-1", "tenant-1", UserRole.EMPLOYEE);

        assertThat(authorizationService.canSubscribeTopic(employee, "trip:trip-2")).isFalse();
    }

    @Test
    void assignedDriverCanPublishAndSubscribeCab() {
        CurrentPrincipal driver = principal("driver-1", "tenant-1", UserRole.DRIVER);

        assertThat(authorizationService.canPublishTrip(driver, "trip-1")).isTrue();
        assertThat(authorizationService.canSubscribeTopic(driver, "cab:cab-1")).isTrue();
    }

    @Test
    void viewPermissionDoesNotAllowPublishing() {
        CurrentPrincipal viewer = principal("viewer-1", "tenant-1", UserRole.EMPLOYEE);

        assertThat(authorizationService.canSubscribeTopic(viewer, "trip:trip-1")).isTrue();
        assertThat(authorizationService.canPublishTrip(viewer, "trip-1")).isFalse();
    }

    @Test
    void invalidTopicIsRejected() {
        CurrentPrincipal employee = principal("employee-1", "tenant-1", UserRole.EMPLOYEE);

        assertThat(authorizationService.canSubscribeTopic(employee, "trip:../trip-1")).isFalse();
        assertThat(authorizationService.canSubscribeTopic(employee, "unknown:trip-1")).isFalse();
    }

    private CurrentPrincipal principal(String userId, String tenantId, UserRole role) {
        return new CurrentPrincipal(userId, tenantId, Set.of(role));
    }
}
