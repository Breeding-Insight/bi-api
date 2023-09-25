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

 CREATE TABLE program_shared_ontology (
      like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
      program_id UUID,
      shared_program_id UUID,
      active boolean NOT NULL DEFAULT false,
      shared_on timestamptz(0) NOT NULL default now(),
      like base_edit_track_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES
 );

ALTER TABLE program_shared_ontology ADD FOREIGN KEY (created_by) REFERENCES bi_user (id);
ALTER TABLE program_shared_ontology ADD FOREIGN KEY (updated_by) REFERENCES bi_user (id);
ALTER TABLE program_shared_ontology ADD FOREIGN KEY (program_id) REFERENCES program (id);
ALTER TABLE program_shared_ontology ADD FOREIGN KEY (shared_program_id) REFERENCES program (id);