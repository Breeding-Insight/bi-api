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

DO $$
    DECLARE
        user_id UUID;
    BEGIN

        user_id := (SELECT id FROM bi_user WHERE name = 'system');

        insert into importer_mapping (id, name, import_type_id, mapping, file, draft, created_at, updated_at, created_by, updated_by)
        values (
                   uuid_generate_v4(),
                   'GenotypicDataImport',
                   'GenotypicDataImport',
                   null,
                   '[]',
                   false,
                   '2022-10-25 03:27:41',
                   '2022-10-25 03:27:41',
                   user_id,
                   user_id
               );
    END $$;