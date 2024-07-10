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

DO $$
DECLARE
user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

INSERT INTO species (common_name, description, created_by, updated_by)
VALUES
    ('Hydrangea', 'TODO', user_id, user_id),
    ('Red Clover', 'TODO', user_id, user_id),
    ('Potato', 'TODO', user_id, user_id),
    ('Blackberry', 'TODO', user_id, user_id),
    ('Raspberry', 'TODO', user_id, user_id),
    ('Sugar beet', 'TODO', user_id, user_id),
    ('Strawberry', 'TODO', user_id, user_id),
    ('Coffee', 'TODO', user_id, user_id),
    ('Hop', 'TODO', user_id, user_id) ON CONFLICT DO NOTHING;

END $$;
