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
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertProgramObservationLevel
insert into program_observation_level(program_id, name, created_by, updated_by)
select program.id, 'Plant', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertProgramOntology
insert into program_ontology (program_id, created_by, updated_by)
select program.id, bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertMethod
insert into method (program_ontology_id, method_name, created_by, updated_by)
select program_ontology.id, 'Test Method', bi_user.id, bi_user.id from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertScale
insert into scale (program_ontology_id, scale_name, data_type, created_by, updated_by)
select program_ontology.id, 'Test Scale', 'TEXT', bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertTrait
insert into trait (program_ontology_id, trait_name, method_id, scale_id, program_observation_level_id, created_by, updated_by)
select program_ontology.id, 'Test Trait', method.id, scale.id, program_observation_level.id, bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join method on method.program_ontology_id = program_ontology.id and method.method_name = 'Test Method'
join scale on scale.program_ontology_id = program_ontology.id and scale.scale_name = 'Test Scale'
join program_observation_level on program_ontology.program_id = program_observation_level.program_id and program_observation_level.name = 'Plant'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertMethod1
insert into method (program_ontology_id, method_name, created_by, updated_by)
select program_ontology.id, 'Test Method1', bi_user.id, bi_user.id from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertScale1
insert into scale (program_ontology_id, scale_name, data_type, created_by, updated_by)
select program_ontology.id, 'Test Scale1', 'TEXT', bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertTrait1
insert into trait (program_ontology_id, trait_name, method_id, scale_id, program_observation_level_id, created_by, updated_by)
select program_ontology.id, 'Test Trait1', method.id, scale.id, program_observation_level.id, bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join method on method.program_ontology_id = program_ontology.id and method.method_name = 'Test Method1'
join scale on scale.program_ontology_id = program_ontology.id and scale.scale_name = 'Test Scale1'
join program_observation_level on program_ontology.program_id = program_observation_level.program_id and program_observation_level.name = 'Plant'
join bi_user on bi_user.name = 'system' limit 1