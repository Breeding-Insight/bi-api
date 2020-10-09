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

CREATE TABLE system_user_role (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    bi_user_id UUID NOT NULL,
    system_role_id UUID NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

CREATE TABLE system_role (
    like base_entity INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES,
    domain text NOT NULL,
    like base_edit_track_entity INCLUDING ALL
);

ALTER TABLE system_user_role ADD FOREIGN KEY (bi_user_id) REFERENCES bi_user (id);
ALTER TABLE system_user_role ADD FOREIGN KEY (system_role_id) REFERENCES system_role (id);

DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

INSERT INTO system_role (domain, created_by, updated_by)
VALUES
('admin', user_id, user_id);

INSERT INTO system_user_role (bi_user_id, system_role_id, created_by, updated_by)
SELECT bi_user.id, system_role.id, user_id, user_id FROM
bi_user JOIN system_role
ON bi_user.name = 'BI-DEV Admin' and system_role.domain = 'admin';


END $$;