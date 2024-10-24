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


-- name: InsertPrograms
insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by, key)
select species.id, 'Program1', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id, 'PA' from species
join bi_user on bi_user.name = 'Test User' limit 1;

insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by, key)
select species.id, 'Program2', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id, 'PB' from species
join bi_user on bi_user.name = 'Test User' limit 1;

insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by, key)
select species.id, 'Program3', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id, 'PC' from species
join bi_user on bi_user.name = 'Test User' limit 1;

insert into program (species_id, name, abbreviation, documentation_url, objective, created_by, updated_by, key)
select species.id, 'Program4', 'test', 'localhost:8080', 'To test things', bi_user.id, bi_user.id, 'PD' from species
join bi_user on bi_user.name = 'Test User' limit 1;

-- name: AddCollaborator

insert into experiment_program_user_role (experiment_id, program_user_role_id, created_by, updated_by)
select
?::uuid, ?::uuid, bi_user.id, bi_user.id
from
bi_user
where bi_user.name = 'system';
;

-- name: InsertProgramRolesMember

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
?::uuid, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Read Only';

-- name: InsertProgramRolesBreeder

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
?::uuid, ?::uuid, role.id, bi_user.id, bi_user.id
from
bi_user
join role on role.domain = 'Program Administrator'
where bi_user.name = 'system';

-- name: InsertProgramRolesExperimentalCollaborator

insert into program_user_role (user_id, program_id, role_id, created_by, updated_by)
select
    ?::uuid, ?::uuid, role.id, bi_user.id, bi_user.id
from
    bi_user
        join role on role.domain = 'Experimental Collaborator'
where bi_user.name = 'system';

-- name: DeleteExperimentalCollaboratorProgramUsers

DELETE FROM program_user_role
    USING role
WHERE program_user_role.role_id = role.id
  AND role.domain = 'Experimental Collaborator';

-- name: DeleteExperimentalCollaborator

DELETE FROM experiment_program_user_role
WHERE id = :id:uuid;


-- name: DeleteProgramUser

DELETE FROM program_user_role
WHERE user_id = :id::uuid;

-- name: InsertSystemRoleAdmin

insert into system_user_role (bi_user_id, system_role_id, created_by, updated_by)
select ?::uuid, system_role.id, bi_user.id, bi_user.id
from
bi_user
join
system_role on system_role.domain = 'System Administrator'
where bi_user.name = 'system';

