-- name: CopyrightNotice
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

-- name: InsertProgram
insert into program (species_id, name, created_by, updated_by)
select species.id, 'Test Program', bi_user.id, bi_user.id from species
join bi_user on bi_user.name = 'system' limit 1;

-- name: InsertProgramUser
insert into program_user_role(program_id, user_id, role_id, active, created_by, updated_by)
select program.id, bi_user.id, role.id, 'true', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'Test User'
join role on role.domain = 'breeder' limit 1

-- name: InsertInactiveProgramUser
insert into program_user_role(program_id, user_id, role_id, active, created_by, updated_by)
select program.id, bi_user.id, role.id, 'false', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'Another Test User'
join role on role.domain = 'breeder' limit 1

-- name: InsertProgramObservationLevel
insert into program_observation_level(program_id, name, created_by, updated_by)
select program.id, 'Plant', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertProgramOntology
insert into program_ontology (program_id, created_by, updated_by)
select program.id, bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertMethod
insert into method (program_ontology_id, created_by, updated_by)
select program_ontology.id, bi_user.id, bi_user.id from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertScale
insert into scale (program_ontology_id, scale_name, data_type, created_by, updated_by)
select program_ontology.id, '1-4 Parlier field response score', 'TEXT', bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertTrait
insert into trait (program_ontology_id, trait_name, abbreviations, method_id, scale_id, program_observation_level_id, created_by, updated_by)
select program_ontology.id, 'Powdery Mildew severity field, leaves', ARRAY['PMSevLeaf', 'PM_LEAF_P4'], method.id, scale.id, program_observation_level.id, bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join method on method.program_ontology_id = program_ontology.id
join scale on scale.program_ontology_id = program_ontology.id and scale.scale_name = '1-4 Parlier field response score'
join program_observation_level on program_ontology.program_id = program_observation_level.program_id and program_observation_level.name = 'Plant'
join bi_user on bi_user.name = 'system' limit 1

-- name: DeleteTrait
delete from trait;
delete from method;
delete from scale;
