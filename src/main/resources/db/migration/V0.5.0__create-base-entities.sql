create extension "uuid-ossp";

CREATE TABLE base_entity (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4()
);

CREATE TABLE base_edit_track_entity (
    created_at_utc timestamptz default timezone('UTC', now()),
    updated_at_utc timestamptz default timezone('UTC', now()),
    created_by UUID,
    updated_by UUID
);