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

-- experiment_id is not tracked in bidb as seperate experiment entities, only in this linking table
CREATE TABLE experiment_program_user_role (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    experiment_id UUID NOT NULL,
    program_user_role_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
);
ALTER TABLE experiment_program_user_role ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE experiment_program_user_role ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
ALTER TABLE experiment_program_user_role ADD FOREIGN KEY (program_user_role_id) REFERENCES program_user_role (id);