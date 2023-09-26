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

-- Create new importer_mapping_program
create table importer_mapping_program (
  like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
  program_id UUID NOT null,
  importer_mapping_id UUID not NULL
);

-- Add foreign keys
ALTER TABLE importer_mapping_program ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE importer_mapping_program ADD FOREIGN KEY (importer_mapping_id) REFERENCES importer_mapping (id);

-- Migrate records from import mapping into new table
insert into importer_mapping_program (program_id, importer_mapping_id)
select program_id, id from importer_mapping;

-- Remove program column from importer_mapping table
alter table importer_mapping drop column program_id;