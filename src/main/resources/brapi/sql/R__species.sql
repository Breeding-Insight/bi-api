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

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
DECLARE
    v_auth_id CONSTANT uuid := 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA';
BEGIN
    /* ------------------------------------------------------------------------------------------
       • uuid_generate_v5(namespace, crop_name)  → deterministic UUID can be used for idempotency
       • Do it this way so no schema changes are required
       • Removed the Honey Bee special case because all systems will be starting fresh
       ------------------------------------------------------------------------------------------ */

    INSERT INTO crop (id, auth_user_id, crop_name)
    SELECT
        uuid_generate_v5('9a4deca9-4068-46a3-9efe-db0c181f491a'::uuid,
        -- 1) lower‑case
        -- 2) trim leading/trailing space
        -- 3) REMOVE every space or tab
        regexp_replace(lower(trim(crop_name)), '\s', '', 'g')),
        v_auth_id,
        crop_name
    FROM (VALUES
        ('Blueberry'), ('Salmon'), ('Grape'), ('Alfalfa'),
        ('Sweet Potato'), ('Trout'), ('Soybean'), ('Cranberry'),
        ('Cucumber'), ('Oat'), ('Citrus'), ('Sugar Cane'),
        ('Strawberry'), ('Honey Bee'), ('Pecan'), ('Lettuce'),
        ('Cotton'), ('Sorghum'), ('Hemp'), ('Hop'),
        ('Hydrangea'), ('Red Clover'), ('Potato'), ('Blackberry'),
        ('Raspberry'), ('Sugar Beet'), ('Coffee'), ('Sunflower')
    ) AS src(crop_name)
    ON CONFLICT (id) DO
        -- want case changes or space changes to overwrite existing
        -- Only rewrite the row if name changed
        UPDATE SET crop_name = EXCLUDED.crop_name
        WHERE crop.crop_name IS DISTINCT FROM EXCLUDED.crop_name;
END $$;
