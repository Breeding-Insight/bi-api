create extension "uuid-ossp";

CREATE TABLE base_entity (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4()
);

CREATE TABLE base_edit_track_entity (
    created_at timestamptz(0) NOT NULL default now(),
    updated_at timestamptz(0) NOT NULL default now(),
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL
);