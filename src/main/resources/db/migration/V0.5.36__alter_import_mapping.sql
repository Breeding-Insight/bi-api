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

-- Mapping table
alter table importer_mapping drop column draft;
alter table importer_mapping drop column file;
alter table importer_mapping rename column import_type_id to importer_template_id;
alter table importer_mapping add column saved boolean default false;

-- Import upload table
alter table importer_import rename to importer_upload;
alter table importer_upload drop column file_data;
alter table importer_upload drop column user_id;
alter table importer_upload drop column modified_data;
alter table importer_upload alter importer_mapping_id drop not null;

-- Progress table
alter table importer_progress alter column statusCode type int;
alter table importer_progress rename column statusCode to status_code;

-- Create table for importer_templates
create table importer_template (
    id int PRIMARY KEY,
    name text unique
);

-- Create records for Germplasm and Experiment Import templates
insert into importer_template (id, name)
values
(1, 'GermplasmImport'),
(2, 'ExperimentImport');

-- Update all of the existing imports
update importer_mapping set importer_template_id = 1 where importer_template_id = 'GermplasmImport';
update importer_mapping set importer_template_id = 2 where importer_template_id = 'ExperimentImport';
alter table importer_mapping alter column importer_template_id type int using (importer_template_id::int);
alter table importer_mapping add foreign key (importer_template_id) references importer_template (id);

