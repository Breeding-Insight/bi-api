--
-- See the NOTICE file distributed with this work for additional information
-- regarding copyright ownership.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TYPE "upload_type" AS ENUM (
  'TRAIT',
  'INVENTORY'
);

CREATE TABLE batch_upload (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    type upload_type NOT NULL,
    program_id UUID NOT NULL,
    user_id UUID NOT NULL,
    data jsonb NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE batch_upload ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE batch_upload ADD FOREIGN KEY (user_id) REFERENCES bi_user (id);
ALTER TABLE batch_upload ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE batch_upload ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
