CREATE TABLE tenants (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    external_subject TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('DRIVER', 'EMPLOYEE', 'TRANSPORT_ADMIN')),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_tenant_external_subject_unique UNIQUE (tenant_id, external_subject)
);

CREATE TABLE cabs (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    driver_id TEXT REFERENCES users(id),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE trips (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    cab_id TEXT NOT NULL REFERENCES cabs(id),
    employee_id TEXT REFERENCES users(id),
    office_id TEXT,
    status TEXT NOT NULL CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trip_permissions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id TEXT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission TEXT NOT NULL CHECK (permission IN ('VIEW', 'PUBLISH', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_permissions_unique UNIQUE (trip_id, user_id, permission)
);

CREATE INDEX users_tenant_id_idx ON users (tenant_id);
CREATE INDEX cabs_tenant_id_idx ON cabs (tenant_id);
CREATE INDEX cabs_driver_id_idx ON cabs (driver_id);
CREATE INDEX trips_tenant_id_idx ON trips (tenant_id);
CREATE INDEX trips_cab_id_idx ON trips (cab_id);
CREATE INDEX trips_employee_id_idx ON trips (employee_id);
CREATE INDEX trips_office_id_idx ON trips (office_id);
CREATE INDEX trip_permissions_trip_id_idx ON trip_permissions (trip_id);
CREATE INDEX trip_permissions_user_id_idx ON trip_permissions (user_id);
