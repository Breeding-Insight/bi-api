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

ALTER TABLE species
    ADD CONSTRAINT unique_common_name UNIQUE (common_name);

DO $$
DECLARE
user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

-- just putting blank strings in for descriptions until later date, can update if needed
INSERT INTO species (common_name, description, created_by, updated_by)
VALUES
    ('Hydrangea', '', user_id, user_id),
    ('Red Clover', '', user_id, user_id),
    ('Potato', '', user_id, user_id),
    ('Blackberry', '', user_id, user_id),
    ('Raspberry', '', user_id, user_id),
    ('Sugar Beet', '', user_id, user_id),
    ('Strawberry', '', user_id, user_id),
    ('Coffee', '', user_id, user_id),
    ('Hop', '', user_id, user_id) ON CONFLICT DO NOTHING;

END $$;
