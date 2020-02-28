CREATE TABLE program (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  species_id UUID NOT NULL,
  name text NOT NULL,
  abbreviation text,
  objective text,
  documentation_url text,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE species (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  common_name text NOT NULL,
  description text,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE program_user_role (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  program_id UUID NOT NULL,
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE role (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  domain text NOT NULL,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE place (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  country_id UUID,
  program_id UUID NOT NULL,
  environment_type_id UUID,
  accessibility_id UUID,
  topography_id UUID,
  name text NOT NULL,
  abbreviation text,
  coordinates geography(POINT, 4326),
  coordinate_uncertainty numeric,
  coordinate_description text,
  slope numeric,
  exposure text,
  documentation_url text,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE country (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  name text NOT NULL,
  alpha_2_code varchar(2),
  alpha_3_code varchar(3),
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE environment_type (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  name text NOT NULL,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE accessibility_option (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  name text NOT NULL,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);

CREATE TABLE topography_option (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  name text NOT NULL,
  like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
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
