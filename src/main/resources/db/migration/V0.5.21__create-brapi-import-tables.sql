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

create table importer_mapping (
	like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
	name TEXT,
	import_type_id TEXT,
    program_id UUID NOT NULL,
    mapping jsonb,
    file jsonb NOT NULL,
    draft bool DEFAULT true,
    like base_edit_track_entity INCLUDING ALL
);

create table importer_import (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    program_id UUID NOT NULL,
    user_id UUID NOT NULL,
    importer_mapping_id UUID NOT NULL,
    importer_progress_id UUID,
    upload_file_name TEXT NOT NULL,
    file_data jsonb,
    modified_data jsonb,
    mapped_data jsonb,
    like base_edit_track_entity INCLUDING ALL
);

create table importer_progress (
	like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
	statusCode SMALLINT,
	message TEXT,
	body JSONB,
    total BIGINT,
    finished BIGINT,
    in_progress BIGINT,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE importer_mapping ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE importer_mapping ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE importer_mapping ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);

ALTER TABLE importer_import ADD FOREIGN KEY (importer_progress_id) REFERENCES importer_progress (id);
ALTER TABLE importer_import ADD FOREIGN KEY (importer_mapping_id) REFERENCES importer_mapping (id);

