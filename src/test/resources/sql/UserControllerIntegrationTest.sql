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

-- name: InsertManyUsers
DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id from bi_user where name = 'system');

insert into bi_user (name, email, created_by, updated_by)
values
('user1', 'user1@user.com', user_id, user_id),
('user2', 'user2@user.com', user_id, user_id),
('user3', 'user3@user.com', user_id, user_id),
('user4', 'user4@user.com', user_id, user_id),
('user5', 'user5@user.com', user_id, user_id),
('user5', 'user5@user.com', user_id, user_id),
('user6', 'user6@user.com', user_id, user_id),
('user7', 'user7@user.com', user_id, user_id),
('user8', 'user8@user.com', user_id, user_id),
('user9', 'user9@user.com', user_id, user_id),
('user10', 'user10@user.com', user_id, user_id),
('user11', 'user11@user.com', user_id, user_id),
('user12', 'user12@user.com', user_id, user_id),
('user13', 'user13@user.com', user_id, user_id),
('user14', 'user14@user.com', user_id, user_id),
('user15', 'user15@user.com', user_id, user_id),
('user16', 'user16@user.com', user_id, user_id),
('user17', 'user17@user.com', user_id, user_id),
('user18', 'user18@user.com', user_id, user_id),
('user19', 'user19@user.com', user_id, user_id),
('user20', 'user20@user.com', user_id, user_id),
('user21', 'user21@user.com', user_id, user_id),
('user22', 'user22@user.com', user_id, user_id),
('user23', 'user23@user.com', user_id, user_id),
('user24', 'user24@user.com', user_id, user_id),
('user25', 'user25@user.com', user_id, user_id),
('user26', 'user26@user.com', user_id, user_id),
('user27', 'user27@user.com', user_id, user_id),
('user28', 'user28@user.com', user_id, user_id),
('user29', 'user29@user.com', user_id, user_id),
('user30', 'user30@user.com', user_id, user_id);

insert into system_user_role (bi_user_id, system_role_id, created_by, updated_by)
select bi_user.id, system_role.id, user_id, user_id from bi_user
join system_role on system_role.domain = 'admin' where bi_user.name like 'user1%';

END $$;
