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

insert into bi_user (id, name, created_by, updated_by)
values
('00000000-0000-0000-0000-000000000000', 'system', '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000');

DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id FROM bi_user WHERE name = 'system');

insert into bi_user (orcid, name, created_by, updated_by)
values
('0000-0003-0437-8310', 'BI-DEV Admin', user_id, user_id);

insert into bi_user (orcid, name, created_by, updated_by)
values
('0000-0002-5527-2711', 'Chris Tucker', user_id, user_id);

insert into bi_user (orcid, name, email, created_by, updated_by)
values
('0000-0002-7156-4503', 'Nick Palladino', 'nicksandbox@mailinator.com', user_id, user_id);

insert into bi_user (orcid, name, email, created_by, updated_by)
values
('1111-2222-3333-4444', 'Test User', 'test@test.com', user_id, user_id);

END $$;