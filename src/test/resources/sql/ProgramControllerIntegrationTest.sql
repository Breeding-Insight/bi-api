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

-- name: DeleteProgram
delete from program_ontology where program_id = ?::uuid;
delete from program_observation_level where program_id = ?::uuid;
delete from program where id = ?::uuid;

-- name: InsertManyPrograms
DO $$
DECLARE
    species_id UUID;
    user_id UUID;
BEGIN

species_id := (SELECT id from species limit 1);
user_id := (SELECT id from bi_user where name = 'system');

insert into public.program (name, species_id, created_by, updated_by) values
('program1', species_id, user_id, user_id),
('program2', species_id, user_id, user_id),
('program3', species_id, user_id, user_id),
('program4', species_id, user_id, user_id),
('program5', species_id, user_id, user_id),
('program6', species_id, user_id, user_id),
('program7', species_id, user_id, user_id),
('program8', species_id, user_id, user_id),
('program9', species_id, user_id, user_id),
('program10', species_id, user_id, user_id),
('program11', species_id, user_id, user_id),
('program12', species_id, user_id, user_id),
('program13', species_id, user_id, user_id),
('program14', species_id, user_id, user_id),
('program15', species_id, user_id, user_id),
('program16', species_id, user_id, user_id),
('program17', species_id, user_id, user_id),
('program18', species_id, user_id, user_id),
('program19', species_id, user_id, user_id),
('program20', species_id, user_id, user_id),
('program21', species_id, user_id, user_id),
('program22', species_id, user_id, user_id),
('program23', species_id, user_id, user_id),
('program24', species_id, user_id, user_id),
('program25', species_id, user_id, user_id),
('program26', species_id, user_id, user_id),
('program27', species_id, user_id, user_id);

END $$;