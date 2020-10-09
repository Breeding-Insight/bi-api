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
insert into program (species_id, name, abbreviation, objective, created_by, updated_by)
select species.id, 'Test Program', 'test', 'To test things', bi_user.id, bi_user.id from species
join bi_user on bi_user.name = 'system' limit 1;

insert into program (species_id, name, abbreviation, objective, created_by, updated_by)
select species.id, 'Test Program1', 'test1', 'To test all the things', bi_user.id, bi_user.id from species
join bi_user on bi_user.name = 'system' limit 1;

-- name: InsertUserProgramAssociations
insert into program_user_role (program_id, user_id, role_id, created_by, updated_by)
select program.id, bi_user.id, role.id, system_user.id, system_user.id
from program
join bi_user on bi_user.name = 'Test User' or bi_user.name = 'Other Test User'
join role on role.domain = 'member'
join bi_user as system_user on system_user.name = 'system'
where program.name = 'Test Program';

insert into program_user_role (program_id, user_id, role_id, active, created_by, updated_by)
select program.id, bi_user.id, role.id, false, system_user.id, system_user.id
from program
join bi_user on bi_user.name = 'Test User' or bi_user.name = 'Other Test User'
join role on role.domain = 'member'
join bi_user as system_user on system_user.name = 'system'
where program.name = 'Test Program1';

-- name: DeactivateProgram
update program set active = false where name = 'Test Program';
