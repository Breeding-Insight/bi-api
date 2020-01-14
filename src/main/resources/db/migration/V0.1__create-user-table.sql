create extension "uuid-ossp";

CREATE TABLE bi_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    orcid varchar(19),
    name VARCHAR(999),
    email VARCHAR(999)
);