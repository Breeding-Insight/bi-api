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

-- name: CreateUser
INSERT INTO bi_user (id, orcid, name, email, created_by, updated_by, active)
VALUES ('594ec70e-0476-4c40-baf5-581ab0cfcd75', '0000-0001-2345-6789', 'Tester', 'tester@mailinator.com', '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000', true);

-- name: CreateProgram
INSERT INTO program (id, species_id, name, created_by, updated_by, active, key, germplasm_sequence, exp_sequence, env_sequence)
SELECT '33a69523-b7b2-4867-94af-a1352c4a69d4', species.id, 'Trail Mix', '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000', true, 'TMTEST', 'tmtest_germplasm_sequence', 'tmtest_exp_sequence', 'tmtest_env_sequence'
FROM species WHERE species.common_name = 'Grape';

-- name: AddUserToProgram
INSERT INTO program_user_role (id, program_id, user_id, role_id, created_by, updated_by, active)
SELECT '0fb8ecf4-3a16-40c6-9c7f-cdfc945967a1', program.id, bi_user.id, role.id, '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000', true
FROM
    bi_user
    JOIN role ON bi_user.name = 'Tester' and role.domain = 'Experimental Collaborator'
    JOIN program ON program.name = 'Trail Mix';
