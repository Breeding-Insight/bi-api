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
join bi_user on bi_user.name = 'system' limit 1

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