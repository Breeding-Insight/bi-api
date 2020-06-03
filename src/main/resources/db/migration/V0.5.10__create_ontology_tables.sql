CREATE TYPE "data_type" AS ENUM (
  'CODE',
  'DATE',
  'DURATION',
  'NOMINAL',
  'NUMERICAL',
  'ORDINAL',
  'TEXT'
);

CREATE TABLE program_ontology (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE program_ontology ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE program_ontology ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE program_ontology ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE method (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_ontology_id UUID NOT NULL,
    method_name text NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE method ADD FOREIGN KEY (program_ontology_id) REFERENCES program_ontology (id);
ALTER TABLE method ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE method ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE scale (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_ontology_id UUID NOT NULL,
    scale_name text NOT NULL,
    data_type data_type NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE scale ADD FOREIGN KEY (program_ontology_id) REFERENCES program_ontology (id);
ALTER TABLE scale ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE scale ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE program_observation_level (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    name text NOT NULL,
    part_of UUID,
    contains UUID,
    active boolean DEFAULT true,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE program_observation_level ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE program_observation_level ADD FOREIGN KEY (part_of) REFERENCES program_observation_level (id);
ALTER TABLE program_observation_level ADD FOREIGN KEY (contains) REFERENCES program_observation_level (id);
ALTER TABLE program_observation_level ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE program_observation_level ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE trait (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_ontology_id UUID NOT NULL,
    trait_name text NOT NULL,
    method_id UUID NOT NULL,
    scale_id UUID NOT NULL,
    program_observation_level_id UUID NOT NULL,
    active boolean DEFAULT true,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE trait ADD FOREIGN KEY (program_ontology_id) REFERENCES program_ontology (id);
ALTER TABLE trait ADD FOREIGN KEY (method_id) REFERENCES method (id);
ALTER TABLE trait ADD FOREIGN KEY (scale_id) REFERENCES scale (id);
ALTER TABLE trait ADD FOREIGN KEY (program_observation_level_id) REFERENCES program_observation_level (id);
ALTER TABLE trait ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE trait ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);


