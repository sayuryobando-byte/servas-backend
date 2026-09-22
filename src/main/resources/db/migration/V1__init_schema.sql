CREATE TYPE document_type AS ENUM ('CC', 'TI', 'CE', 'PASSPORT', 'OTRO');
CREATE TYPE modality AS ENUM ('VIRTUAL', 'PRESENCIAL');
CREATE TYPE reservation_status AS ENUM ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED');
CREATE TYPE cancelled_by AS ENUM ('CLIENT', 'PROVIDER');

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE comunas (
    id   INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE providers (
    id              UUID PRIMARY KEY,
    user_id         UUID          NOT NULL UNIQUE REFERENCES users (id),
    document_type   document_type NOT NULL,
    document_number VARCHAR(50)   NOT NULL UNIQUE,
    first_name      VARCHAR(100)  NOT NULL,
    last_name       VARCHAR(100)  NOT NULL,
    birth_date      DATE          NOT NULL,
    phone           VARCHAR(11)   NOT NULL
);

CREATE TABLE companies (
    id           UUID PRIMARY KEY,
    provider_id  UUID        NOT NULL REFERENCES providers (id),
    nit          VARCHAR(50) NOT NULL UNIQUE,
    name         VARCHAR(150) NOT NULL,
    description  TEXT,
    address      VARCHAR(255),
    social_media VARCHAR(255),
    logo_url     VARCHAR(255)
);

CREATE TABLE services (
    id                UUID PRIMARY KEY,
    company_id        UUID           NOT NULL REFERENCES companies (id),
    comuna_id         INTEGER        REFERENCES comunas (id),
    name              VARCHAR(100)   NOT NULL,
    modality          modality       NOT NULL,
    cost              NUMERIC(10, 2) NOT NULL,
    duration_minutes  INTEGER        NOT NULL,
    description       TEXT,
    recommendations   TEXT,
    start_date        DATE,
    end_date          DATE,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE schedules (
    id          UUID PRIMARY KEY,
    service_id  UUID      NOT NULL REFERENCES services (id),
    day_of_week INTEGER   NOT NULL,
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL
);

CREATE TABLE blocked_dates (
    id         UUID PRIMARY KEY,
    company_id UUID        NOT NULL REFERENCES companies (id),
    service_id UUID        REFERENCES services (id),
    block_date DATE        NOT NULL,
    reason     VARCHAR(255)
);

CREATE TABLE clients (
    id              UUID PRIMARY KEY,
    document_type   document_type NOT NULL,
    document_number VARCHAR(50)   NOT NULL,
    first_name      VARCHAR(100)  NOT NULL,
    last_name       VARCHAR(100)  NOT NULL,
    phone           VARCHAR(15)   NOT NULL,
    email           VARCHAR(100)  NOT NULL,
    CONSTRAINT uk_clients_document UNIQUE (document_type, document_number)
);

CREATE TABLE reservations (
    id                 UUID PRIMARY KEY,
    service_id         UUID               NOT NULL REFERENCES services (id),
    client_id          UUID               NOT NULL REFERENCES clients (id),
    reservation_date   DATE               NOT NULL,
    start_time         TIME               NOT NULL,
    end_time           TIME               NOT NULL,
    status             reservation_status NOT NULL,
    created_at         TIMESTAMPTZ        NOT NULL DEFAULT now(),
    cancelled_by       cancelled_by,
    cancellation_reason TEXT
);

CREATE INDEX idx_reservations_service_date ON reservations (service_id, reservation_date);
CREATE INDEX idx_services_company ON services (company_id);
CREATE INDEX idx_schedules_service ON schedules (service_id);
CREATE INDEX idx_blocked_dates_company ON blocked_dates (company_id);