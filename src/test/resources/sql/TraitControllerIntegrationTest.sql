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
insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by)
select species.id, 'Test Program', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id from species
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
insert into trait (program_ontology_id, trait_name, abbreviations, method_id, scale_id, program_observation_level_id, created_by, updated_by)
select program_ontology.id, 'Test Trait', ARRAY['t1', 't2'], method.id, scale.id, program_observation_level.id, bi_user.id, bi_user.id
from program_ontology
join program on program.id = program_ontology.program_id and program.name = 'Test Program'
join method on method.program_ontology_id = program_ontology.id and method.method_name = 'Test Method'
join scale on scale.program_ontology_id = program_ontology.id and scale.scale_name = 'Test Scale'
join program_observation_level on program_ontology.program_id = program_observation_level.program_id and program_observation_level.name = 'Plant'
join bi_user on bi_user.name = 'system' limit 1

-- name: DeleteTrait
delete from trait;
delete from method;
delete from scale;

-- name: InsertOtherProgram
insert into program (species_id, name, created_by, updated_by)
select species.id, 'Other Test Program', bi_user.id, bi_user.id from species
join bi_user on bi_user.name = 'system' limit 1

-- name: InsertOtherProgramObservationLevel
insert into program_observation_level(program_id, name, created_by, updated_by)
select program.id, 'Plant', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Other Test Program' limit 1

-- name: InsertOtherProgramOntology
insert into program_ontology (program_id, created_by, updated_by)
select program.id, bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Other Test Program' limit 1

-- name: InsertManyTraits
DO $$
DECLARE
    user_id UUID;
    program_ontology_id UUID;
    method_id UUID;
    scale_id UUID;
    program_observation_level_id UUID;
BEGIN

user_id := (SELECT id from bi_user where name = 'system');
program_ontology_id := (SELECT program_ontology.id from program_ontology join program on program.id = program_ontology.program_id and program.name = 'Other Test Program');
method_id := (SELECT method.id from method where method.method_name = 'Test Method');
scale_id := (SELECT scale.id from scale where scale.scale_name = 'Test Scale');
program_observation_level_id := (SELECT program_observation_level.id from program_observation_level join program on program.id = program_observation_level.program_id and program.name = 'Test Program');

insert into trait (trait_name, program_ontology_id, abbreviations, method_id, scale_id, program_observation_level_id, created_by, updated_by)
values
('trait1', program_ontology_id, ARRAY['1', 't1'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait2', program_ontology_id, ARRAY['2', 't2'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait3', program_ontology_id, ARRAY['3', 't3'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait4', program_ontology_id, ARRAY['4', 't4'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait5', program_ontology_id, ARRAY['5', 't5'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait6', program_ontology_id, ARRAY['6', 't6'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait7', program_ontology_id, ARRAY['7', 't7'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait8', program_ontology_id, ARRAY['8', 't8'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait9', program_ontology_id, ARRAY['9', 't9'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait10', program_ontology_id, ARRAY['10', 't10'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait11', program_ontology_id, ARRAY['11', 't11'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait12', program_ontology_id, ARRAY['12', 't12'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait13', program_ontology_id, ARRAY['13', 't13'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait14', program_ontology_id, ARRAY['14', 't14'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait15', program_ontology_id, ARRAY['15', 't15'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait16', program_ontology_id, ARRAY['16', 't16'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait17', program_ontology_id, ARRAY['17', 't17'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait18', program_ontology_id, ARRAY['18', 't18'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait19', program_ontology_id, ARRAY['19', 't19'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait20', program_ontology_id, ARRAY['20', 't20'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait21', program_ontology_id, ARRAY['21', 't21'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait22', program_ontology_id, ARRAY['22', 't22'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait23', program_ontology_id, ARRAY['23', 't23'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait24', program_ontology_id, ARRAY['24', 't24'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait25', program_ontology_id, ARRAY['25', 't25'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait26', program_ontology_id, ARRAY['26', 't26'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait27', program_ontology_id, ARRAY['27', 't27'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait28', program_ontology_id, ARRAY['28', 't28'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait29', program_ontology_id, ARRAY['29', 't29'], method_id, scale_id, program_observation_level_id, user_id, user_id),
('trait30', program_ontology_id, ARRAY['30', 't30'], method_id, scale_id, program_observation_level_id, user_id, user_id);

END $$;
