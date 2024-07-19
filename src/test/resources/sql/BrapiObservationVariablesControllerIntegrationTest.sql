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

-- name: InsertProgramObservationLevel
insert into program_observation_level(program_id, name, created_by, updated_by)
select program.id, 'Plant', bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertProgramOntology
insert into program_ontology (program_id, created_by, updated_by)
select program.id, bi_user.id, bi_user.id from program
join bi_user on bi_user.name = 'system' and program.name = 'Test Program' limit 1

-- name: InsertTestProgramUser
insert into program_user_role (program_id, user_id, role_id, created_by, updated_by)
select program.id, bi_user.id, role.id, bi_user.id, bi_user.id from bi_user
join program on program.name = 'Test Program'
join role on role.domain = 'Read Only'
where bi_user.name = 'Test User'

-- name: InsertOtherTestProgramUser
insert into program_user_role (program_id, user_id, role_id, created_by, updated_by)
select program.id, bi_user.id, role.id, bi_user.id, bi_user.id from bi_user
join program on program.name = 'Test Program'
join role on role.domain = 'Read only'
where bi_user.name = 'Other Test User'

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

-- name: InsertOtherTestOtherProgramUser
insert into program_user_role (program_id, user_id, role_id, created_by, updated_by)
select program.id, bi_user.id, role.id, bi_user.id, bi_user.id from bi_user
join program on program.name = 'Other Test Program'
join role on role.domain = 'Read Only'
where bi_user.name = 'Other Test User'

