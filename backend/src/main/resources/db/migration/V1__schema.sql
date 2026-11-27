-- Arrumo: full schema.
-- Times are UTC (timestamptz). Weights in kg with 3 decimal places.
-- From now on every change is a new migration (V3__..., V4__...). Never edit this one.

-- ---------------------------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------------------------
CREATE TABLE app_user (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(40)  NOT NULL UNIQUE,
    name                 VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(200) NOT NULL,
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','MANAGER','OPERATOR','VIEWER','LOGISTICS')),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    mfa_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    mfa_secret           VARCHAR(200),                 -- encrypted with AES-256-GCM (APP_ENCRYPTION_KEY)
    mfa_last_step        BIGINT       NOT NULL DEFAULT 0, -- stops the same code from being used twice
    failed_attempts      INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT username_format CHECK (username ~ '^[a-z0-9._-]{3,40}$')
);

-- ---------------------------------------------------------------------------------------------
-- Products, lots (raw material) and pallets (finished goods)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE product (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,          -- ERP product code
    description VARCHAR(200) NOT NULL,
    gtin        VARCHAR(14)  UNIQUE CHECK (gtin IS NULL OR gtin ~ '^[0-9]{8,14}$'), -- for GS1 labels
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Raw-material lot. It is either in a location (holding empty) or in a list: corridor or leftovers.
CREATE TABLE lot (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30)   NOT NULL UNIQUE,
    product_id      BIGINT        REFERENCES product(id), -- empty = scanned but no ERP data yet (orange)
    weight_kg       NUMERIC(12,3) CHECK (weight_kg IS NULL OR weight_kg >= 0),
    production_date DATE,
    notes           VARCHAR(500)  NOT NULL DEFAULT '',
    status          VARCHAR(12)   NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN ('IN_STOCK','SHIPPED')),
    holding         VARCHAR(10)   CHECK (holding IN ('CORRIDOR','LEFTOVERS')),
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX lot_product_idx ON lot(product_id);
CREATE INDEX lot_status_idx ON lot(status);
CREATE INDEX lot_holding_idx ON lot(holding) WHERE holding IS NOT NULL;

-- Finished-goods pallet (GS1 label). One lot can be spread over several pallets.
CREATE TABLE pallet (
    id              BIGSERIAL PRIMARY KEY,
    sscc            VARCHAR(18)   UNIQUE CHECK (sscc IS NULL OR sscc ~ '^[0-9]{18}$'),
    lot             VARCHAR(30),
    gtin            VARCHAR(14),
    product_id      BIGINT        REFERENCES product(id),  -- chosen by hand, otherwise taken from the GTIN
    weight_kg       NUMERIC(12,3) CHECK (weight_kg IS NULL OR weight_kg >= 0),
    boxes           INT           CHECK (boxes IS NULL OR boxes >= 0),
    production_date DATE,
    expiry_date     DATE,
    notes           VARCHAR(500)  NOT NULL DEFAULT '',
    status          VARCHAR(12)   NOT NULL DEFAULT 'IN_STOCK' CHECK (status IN ('IN_STOCK','SHIPPED')),
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX pallet_lot_idx ON pallet(lot);
CREATE INDEX pallet_status_idx ON pallet(status);

-- Finished-goods stock as exported by the ERP (one row per lot, with the total). Each import replaces it all.
CREATE TABLE erp_fg_stock (
    lot         VARCHAR(30)  PRIMARY KEY,
    product_id  BIGINT       REFERENCES product(id) ON DELETE SET NULL,
    quantity    VARCHAR(80),
    weight_kg   NUMERIC(12,3),
    boxes       NUMERIC(12,3),
    notes       VARCHAR(500) NOT NULL DEFAULT '',
    imported_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------------------------
-- Map: zones and locations
-- ---------------------------------------------------------------------------------------------
CREATE TABLE zone (
    id            VARCHAR(10) PRIMARY KEY,
    name          VARCHAR(60) NOT NULL,
    sort_order    INT         NOT NULL,
    column_labels TEXT        NOT NULL   -- column labels separated by ';' (Rack 1;Rack 2;...)
);

-- Every physical slot. Holds a lot (raw material), a pallet (finished goods) or a free-text note.
CREATE TABLE location (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(10) NOT NULL UNIQUE,     -- R514a (rack 5, floor 1, position 4, slot a) or R324
    zone_id     VARCHAR(10) NOT NULL REFERENCES zone(id),
    group_label VARCHAR(10) NOT NULL DEFAULT '', -- rows that make up one position (spaced apart on screen)
    row_no      INT         NOT NULL,
    col_no      INT         NOT NULL,
    lot_id      BIGINT      REFERENCES lot(id),
    pallet_id   BIGINT      REFERENCES pallet(id),
    note        VARCHAR(120),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(40),
    version     BIGINT      NOT NULL DEFAULT 0,
    -- a lot / a pallet can only be in one place; deferred so a move can happen inside one transaction
    CONSTRAINT location_lot_unique    UNIQUE (lot_id)    DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT location_pallet_unique UNIQUE (pallet_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT location_one_content   CHECK (num_nonnulls(lot_id, pallet_id, note) <= 1),
    CONSTRAINT location_slot_unique   UNIQUE (zone_id, row_no, col_no)
);

-- ---------------------------------------------------------------------------------------------
-- History and audit: append-only, never changed (traceability)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE movement (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    username      VARCHAR(40)  NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    lot_code      VARCHAR(30),
    from_location VARCHAR(20),
    to_location   VARCHAR(20),
    note          VARCHAR(300) NOT NULL DEFAULT '',
    area          VARCHAR(2)   NOT NULL DEFAULT 'RM' CHECK (area IN ('RM','FG')) -- logistics users only see FG
);
CREATE INDEX movement_lot_idx ON movement(lot_code);
CREATE INDEX movement_occurred_idx ON movement(occurred_at DESC);

CREATE TABLE audit_entry (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    username    VARCHAR(40),
    ip          VARCHAR(64),
    action      VARCHAR(40)  NOT NULL,
    detail      VARCHAR(500) NOT NULL DEFAULT ''
);
CREATE INDEX audit_entry_occurred_idx ON audit_entry(occurred_at DESC);

CREATE FUNCTION append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Table % only accepts new rows', TG_TABLE_NAME;
END $$;

CREATE TRIGGER movement_immutable      BEFORE UPDATE OR DELETE ON movement    FOR EACH ROW       EXECUTE FUNCTION append_only();
CREATE TRIGGER audit_entry_immutable   BEFORE UPDATE OR DELETE ON audit_entry FOR EACH ROW       EXECUTE FUNCTION append_only();
CREATE TRIGGER movement_no_truncate    BEFORE TRUNCATE ON movement            FOR EACH STATEMENT EXECUTE FUNCTION append_only();
CREATE TRIGGER audit_entry_no_truncate BEFORE TRUNCATE ON audit_entry         FOR EACH STATEMENT EXECUTE FUNCTION append_only();

-- ---------------------------------------------------------------------------------------------
-- Packing board (kanban, admin only) and settings
-- ---------------------------------------------------------------------------------------------
CREATE TABLE packing_column (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL,
    sort_order INT         NOT NULL,
    daily_list BOOLEAN     NOT NULL DEFAULT FALSE, -- cards here go into the 17:00 list
    reports    BOOLEAN     NOT NULL DEFAULT FALSE  -- a card that lands here is reported on Teams
);

CREATE TABLE packing_card (
    id          BIGSERIAL PRIMARY KEY,
    column_id   BIGINT        NOT NULL REFERENCES packing_column(id),
    sort_order  INT           NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    product     VARCHAR(250)  NOT NULL DEFAULT '',
    quantity    VARCHAR(60)   NOT NULL DEFAULT '',
    notes       VARCHAR(2000) NOT NULL DEFAULT '',
    archived    BOOLEAN       NOT NULL DEFAULT FALSE,
    reported_at TIMESTAMPTZ,
    send_error  VARCHAR(300),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX packing_card_column_idx ON packing_card(column_id, sort_order) WHERE NOT archived;

CREATE TABLE packing_dispatch (                    -- log of what was sent to Power Automate
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    type        VARCHAR(20)  NOT NULL,
    ok          BOOLEAN      NOT NULL,
    detail      VARCHAR(500) NOT NULL DEFAULT ''
);

-- Changed by the admin in the app (e.g. Power Automate URLs). Values are encrypted (AES-256-GCM).
CREATE TABLE app_setting (
    setting_key     VARCHAR(60) PRIMARY KEY,
    encrypted_value TEXT        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(60) NOT NULL
);
