CREATE TABLE bi_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    orcid text,
    name text,
    email text
);

CREATE TABLE program (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  species_id UUID NOT NULL,
  name text,
  abbreviation text,
  objective text,
  documentation_url text
);

CREATE TABLE species (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  common_name text,
  description text
);

CREATE TABLE program_user_role (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  program_id UUID NOT NULL,
  user_id UUID NOT NULL,
  role_id UUID NOT NULL
);

CREATE TABLE role (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  domain text
);

CREATE TABLE place (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  country_id UUID,
  program_id UUID NOT NULL,
  environment_type_id UUID,
  accessibility_id UUID,
  topography_id UUID,
  name text,
  abbreviation text,
  coordinates geography(POINT, 4326),
  coordinate_uncertainty numeric,
  coordinate_description text,
  slope numeric,
  exposure text,
  documentation_url text
);

CREATE TABLE country (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text,
  alpha_2_code varchar(2),
  alpha_3_code varchar(3)
);

CREATE TABLE environment_type (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text
);

CREATE TABLE accessibility_option (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text
);

CREATE TABLE topography_option (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name text
);

ALTER TABLE program ADD FOREIGN KEY (species_id) REFERENCES species (id);

ALTER TABLE program_user_role ADD FOREIGN KEY (program_id) REFERENCES program (id);

ALTER TABLE program_user_role ADD FOREIGN KEY (user_id) REFERENCES bi_user (id);

ALTER TABLE program_user_role ADD FOREIGN KEY (role_id) REFERENCES role (id);

ALTER TABLE place ADD FOREIGN KEY (country_id) REFERENCES country (id);

ALTER TABLE place ADD FOREIGN KEY (program_id) REFERENCES program (id);

ALTER TABLE place ADD FOREIGN KEY (environment_type_id) REFERENCES environment_type (id);

ALTER TABLE place ADD FOREIGN KEY (accessibility_id) REFERENCES accessibility_option (id);

ALTER TABLE place ADD FOREIGN KEY (topography_id) REFERENCES topography_option (id);

COMMENT ON COLUMN program.id IS 'Used as external reference to BrAPI Programs';

COMMENT ON COLUMN place.id IS 'Used as external reference to BrAPI Locations';
