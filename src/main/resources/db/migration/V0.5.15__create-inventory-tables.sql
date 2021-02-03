/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE TYPE "entity_level" AS ENUM (
  'POPULATION',
  'INDIVIDUAL'
);

CREATE TYPE "entity_relationship_type" AS ENUM (
  'POPULATION_OF'
);

CREATE TABLE base_type (
    program_id UUID NOT NULL,
    type text NOT NULL,
    active boolean DEFAULT true
);

CREATE TABLE entity (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    name text NOT NULL,
    level entity_level NOT NULL,
    entity_cross_id UUID,
    active boolean DEFAULT true,
    status_date timestamptz(0),
    status_by UUID,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE entity ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE entity ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
COMMENT ON COLUMN entity.id IS 'Used as externalReference to Brapi Germplasm';

CREATE TABLE entity_relationship (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    type entity_relationship_type NOT NULL,
    relation_source_id UUID NOT NULL,
    relation_target_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE entity_relationship ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE entity_relationship ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE entity_cross (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    mother_entity_id UUID NOT NULL,
    father_entity_id UUID,
    name text,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE entity_cross ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE entity_cross ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
COMMENT ON COLUMN entity_cross.id IS 'Used as externalReference to Brapi Cross';

CREATE TABLE inventory (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    active boolean DEFAULT true,
    amount integer,
    units_id UUID NOT NULL,
    acquisition_date timestamptz(0),
    inventory_type_id UUID NOT NULL,
    place_id UUID,
    geolocation jsonb,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
COMMENT ON COLUMN inventory.id IS 'Used as externalReference to Brapi Seed lot';

CREATE TABLE inventory_position_details (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    inventory_id UUID NOT NULL,
    inventory_position_details_type_id UUID NOT NULL,
    value text NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_position_details ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_position_details ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_position_details_type (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    like base_type INCLUDING ALL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_position_details_type ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory_position_details_type ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_position_details_type ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_type (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    like base_type INCLUDING ALL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_type ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory_type ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_type ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_relationship (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    inventory_relationship_type_id UUID NOT NULL,
    relation_source_id UUID NOT NULL,
    relation_target_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_relationship ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_relationship ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_relationship_type (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    like base_type INCLUDING ALL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_relationship_type ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory_relationship_type ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_relationship_type ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_source (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    inventory_source_type_id UUID NOT NULL,
    relation_source_id UUID NOT NULL,
    relation_target_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_source ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_source ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_source_type (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    type text NOT NULL,
    description text,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_source_type ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory_source_type ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_source_type ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

CREATE TABLE inventory_unit_type (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    like base_type INCLUDING ALL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE inventory_unit_type ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory_unit_type ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE inventory_unit_type ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

ALTER TABLE entity ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE entity ADD FOREIGN KEY (entity_cross_id) REFERENCES entity_cross (id);
ALTER TABLE entity ADD FOREIGN KEY (status_by) REFERENCES bi_user (id);

ALTER TABLE entity_relationship ADD FOREIGN KEY (relation_source_id) REFERENCES entity (id);
ALTER TABLE entity_relationship ADD FOREIGN KEY (relation_target_id) REFERENCES entity (id);

ALTER TABLE entity_cross ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE entity_cross ADD FOREIGN KEY (mother_entity_id) REFERENCES entity (id);
ALTER TABLE entity_cross ADD FOREIGN KEY (father_entity_id) REFERENCES entity (id);

ALTER TABLE inventory ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE inventory ADD FOREIGN KEY (entity_id) REFERENCES entity (id);
ALTER TABLE inventory ADD FOREIGN KEY (units_id) REFERENCES inventory_unit_type (id);
ALTER TABLE inventory ADD FOREIGN KEY (inventory_type_id) REFERENCES inventory_type (id);
ALTER TABLE inventory ADD FOREIGN KEY (place_id) REFERENCES place (id);

ALTER TABLE inventory_position_details ADD FOREIGN KEY (inventory_id) REFERENCES inventory (id);
ALTER TABLE inventory_position_details ADD FOREIGN KEY (inventory_position_details_type_id) REFERENCES inventory_position_details_type (id);

ALTER TABLE inventory_relationship ADD FOREIGN KEY (inventory_relationship_type_id) REFERENCES inventory_relationship_type (id);
ALTER TABLE inventory_relationship ADD FOREIGN KEY (relation_source_id) REFERENCES inventory (id);
ALTER TABLE inventory_relationship ADD FOREIGN KEY (relation_target_id) REFERENCES inventory (id);

ALTER TABLE inventory_source ADD FOREIGN KEY (inventory_source_type_id) REFERENCES inventory_source_type (id);
ALTER TABLE inventory_source ADD FOREIGN KEY (relation_source_id) REFERENCES inventory (id);
ALTER TABLE inventory_source ADD FOREIGN KEY (relation_target_id) REFERENCES inventory (id);
