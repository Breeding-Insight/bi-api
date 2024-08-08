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

-- name: InsertOtherProgram
insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by, key)
select species.id, 'Other Test Program', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id, 'OT' from species
join bi_user on bi_user.name = 'Test User' limit 1;

-- name: InsertOtherProgramObservationLevel
insert into program_observation_level (program_id, name, created_by, updated_by)
select program.id, 'Plant', bi_user.id, bi_user.id from
program
join bi_user on bi_user.name = 'Test User' limit 1;

-- name: DeleteProgram
delete from program_ontology where program_id = ?::uuid;
delete from program_observation_level where program_id = ?::uuid;
delete from program_breeding_method where program_id = ?::uuid;
delete from program_enabled_breeding_methods where program_id = ?::uuid;
delete from program where id = ?::uuid;

-- name: InsertManyPrograms
DO $$
DECLARE
    species_id UUID;
    user_id UUID;
BEGIN

species_id := (SELECT id from species limit 1);
user_id := (SELECT id from bi_user where name = 'system');

insert into public.program (name, species_id, created_by, updated_by, key) values
('program1', species_id, user_id, user_id, 'PA'),
('program2', species_id, user_id, user_id, 'PB'),
('program3', species_id, user_id, user_id, 'PC'),
('program4', species_id, user_id, user_id, 'PD'),
('program5', species_id, user_id, user_id, 'PE'),
('program6', species_id, user_id, user_id, 'PF'),
('program7', species_id, user_id, user_id, 'PG'),
('program8', species_id, user_id, user_id, 'PH'),
('program9', species_id, user_id, user_id, 'PI'),
('program10', species_id, user_id, user_id, 'PJ'),
('program11', species_id, user_id, user_id, 'PK'),
('program12', species_id, user_id, user_id, 'PL'),
('program13', species_id, user_id, user_id, 'PM'),
('program14', species_id, user_id, user_id, 'PN'),
('program15', species_id, user_id, user_id, 'PO'),
('program16', species_id, user_id, user_id, 'PP'),
('program17', species_id, user_id, user_id, 'PQ'),
('program18', species_id, user_id, user_id, 'PR'),
('program19', species_id, user_id, user_id, 'PS'),
('program20', species_id, user_id, user_id, 'PT'),
('program21', species_id, user_id, user_id, 'PU'),
('program22', species_id, user_id, user_id, 'PV'),
('program23', species_id, user_id, user_id, 'PW'),
('program24', species_id, user_id, user_id, 'PX'),
('program25', species_id, user_id, user_id, 'PY'),
('program26', species_id, user_id, user_id, 'PZ'),
('program27', species_id, user_id, user_id, 'PAA');

END $$;

-- name: InsertManyProgramUsers

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
bi_user.id, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Read Only'
where
bi_user.name like 'user1%';

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
bi_user.id, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Program Administrator'
where
bi_user.name like 'user2%';

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
bi_user.id, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Read Only'
where
bi_user.name like 'user2%';

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
bi_user.id, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Program Administrator'
where
bi_user.name like 'user9';