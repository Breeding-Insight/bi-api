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

 -- name: InsertProgramLocations
DO $$
DECLARE
    user_id UUID;
    program_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');
program_id := (SELECT id from program where name = 'Test Program' limit 1);

insert into place (name, abbreviation, created_by, updated_by, program_id, slope, created_at)
values
('place1', 'abbrev1', user_id, user_id, program_id, 1.1, '2020-09-30 10:14:00'),
('place2', 'abbrev2', user_id, user_id, program_id, 1.3, '2020-09-30 10:14:01'),
('place3', 'abbrev3', user_id, user_id, program_id, 2, '2020-09-30 10:14:02'),
('place4', 'abbrev4', user_id, user_id, program_id, 2.5, '2019-09-30 10:14:00'),
('place5', 'abbrev5', user_id, user_id, program_id, 6, '2020-09-30 10:15:00'),
('place6', 'abbrev6', user_id, user_id, program_id, 7.2, '2018-09-30 10:14:00'),
('place7', 'abbrev7', user_id, user_id, program_id, 2.3, '2020-05-30 10:14:00'),
('place8', 'abbrev8', user_id, user_id, program_id, 5.5, '2020-09-29 10:14:00'),
('place9', 'abbrev9', user_id, user_id, program_id, 6.6, '2020-09-30 10:14:03'),
('place10', 'abbrev10', user_id, user_id, program_id, 7.7, '2020-09-18 10:14:00'),
('place11', 'abbrev11', user_id, user_id, program_id, 8.4, '2020-09-19 10:14:00'),
('place12', 'abbrev12', user_id, user_id, program_id, 2.1, '2020-09-20 10:14:00'),
('place13', 'abbrev13', user_id, user_id, program_id, null, '2020-09-21 10:14:00'),
('place14', 'abbrev14', user_id, user_id, program_id, null, '2020-09-22 10:14:00'),
('place15', 'abbrev15', user_id, user_id, program_id, null, '2020-09-23 10:14:00'),
('place16', 'abbrev16', user_id, user_id, program_id, 0, '2020-09-24 10:14:00'),
('place17', 'abbrev17', user_id, user_id, program_id, null, '2020-09-25 10:14:00'),
('place18', 'abbrev18', user_id, user_id, program_id, -1, '2020-09-26 10:14:00'),
('place19', 'abbrev19', user_id, user_id, program_id, null, '2020-09-27 10:14:00'),
('place20', 'abbrev20', user_id, user_id, program_id, -1.1, '2020-09-30 10:16:00'),
('place21', 'abbrev21', user_id, user_id, program_id, -1.2, '2020-09-30 10:17:00'),
('place22', 'abbrev22', user_id, user_id, program_id, -3.2, '2020-09-30 10:18:00'),
('place23', 'abbrev23', user_id, user_id, program_id, null, '2020-09-30 10:19:00'),
('place24', 'abbrev24', user_id, user_id, program_id, null, '2020-09-30 10:20:00'),
('place25', null, user_id, user_id, program_id, null, '2020-09-30 10:21:00'),
('place26', null, user_id, user_id, program_id, null, '2020-09-30 10:22:00'),
('place27', null, user_id, user_id, program_id, null, '2020-09-30 10:23:00'),
('place28', null, user_id, user_id, program_id, null, '2020-09-30 10:24:00'),
('place29', null, user_id, user_id, program_id, null, '2020-09-30 10:25:00'),
('place30', null, user_id, user_id, program_id, null, '2020-09-30 10:26:00');

END $$;
