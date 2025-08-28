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

-- Rename orcid column to more generic oauth_id.
ALTER TABLE bi_user
RENAME COLUMN orcid TO oauth_id;

-- Rename unique constraint.
ALTER TABLE bi_user
RENAME CONSTRAINT orcid_unique TO oauth_id_unique;

-- Add a column to store the OAuth provider with 'orcid' as the default value.
ALTER TABLE bi_user
ADD COLUMN oauth_provider text DEFAULT 'orcid';
