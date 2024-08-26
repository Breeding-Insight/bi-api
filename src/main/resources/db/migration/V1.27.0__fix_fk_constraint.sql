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

DO $$
BEGIN
     -- Drop foreign key constraint (can't directly alter ON DELETE behavior).
    ALTER TABLE experiment_program_user_role DROP CONSTRAINT experiment_program_user_role_program_user_role_id_fkey;
    -- Add foreign key constraint with ON DELETE CASCADE.
    ALTER TABLE experiment_program_user_role
    ADD CONSTRAINT experiment_program_user_role_program_user_role_id_fkey
        FOREIGN KEY (program_user_role_id) REFERENCES program_user_role (id)
        ON DELETE CASCADE;
END $$;
