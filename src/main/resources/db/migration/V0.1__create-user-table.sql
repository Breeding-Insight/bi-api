create extension "uuid-ossp";

CREATE TABLE bi_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    orcid text,
    name text,
    email text
);