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

create table batch_upload_progress (
	like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
	statusCode SMALLINT,
	message TEXT,
    total BIGINT,
    finished BIGINT,
    in_progress BIGINT,
    like base_edit_track_entity INCLUDING ALL
);

alter table batch_upload add column batch_upload_progress_id UUID;
ALTER TABLE batch_upload ADD FOREIGN KEY (batch_upload_progress_id) REFERENCES batch_upload_progress (id);