package com.example.livetripupdates.persistence;

import com.example.livetripupdates.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {
}
