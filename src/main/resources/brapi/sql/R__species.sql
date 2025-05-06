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

-- for uuid_generate_v4()
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
DECLARE
    v_auth_id constant uuid := 'AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA';
BEGIN
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Blueberry') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Salmon') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Grape') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Alfalfa') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Sweet Potato') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Trout') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Soybean') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Cranberry') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Cucumber') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Oat') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Citrus') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Sugar Cane') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Strawberry') ON CONFLICT DO NOTHING;
    -- for the Honey Bee case, want to overwrite name, not preserve existing
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Honey Bee') ON CONFLICT (id) DO UPDATE SET crop_name = EXCLUDED.crop_name;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Pecan') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Lettuce') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Cotton') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Sorghum') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Hemp') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Hop') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Hydrangea') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Red Clover') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Potato') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Blackberry') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Raspberry') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Sugar Beet') ON CONFLICT DO NOTHING;
    INSERT INTO crop (id, auth_user_id, crop_name) VALUES (uuid_generate_v4(), v_auth_id, 'Coffee') ON CONFLICT DO NOTHING;
END $$;