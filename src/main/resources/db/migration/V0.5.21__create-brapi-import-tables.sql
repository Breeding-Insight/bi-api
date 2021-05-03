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

create table import_mapping (
	like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
	name TEXT,
	import_type_id TEXT,
    program_id UUID NOT NULL,
    mapping jsonb,
    file jsonb NOT NULL,
    draft bool DEFAULT true,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE import_mapping ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE import_mapping ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE import_mapping ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

alter table batch_upload add column modified_data jsonb;

alter table batch_upload add column mapped_data jsonb;

ALTER TABLE batch_upload ALTER COLUMN "type" TYPE VARCHAR(255);
DROP TYPE IF EXISTS upload_type;
CREATE TYPE "upload_type" AS ENUM (
  'TRAIT',
  'INVENTORY',
  'BRAPI'
);
ALTER TABLE batch_upload ALTER COLUMN "type" TYPE upload_type using (type::upload_type);

