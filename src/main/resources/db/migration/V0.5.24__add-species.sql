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

insert into species (common_name, description, created_by, updated_by)
values
('Soybean', 'The soybean, soy bean, or soya bean is a species of legume native to East Asia, widely grown for its edible bean, which has numerous uses.', user_id, user_id),
('Cranberry', 'Cranberries are a group of evergreen dwarf shrubs or trailing vines in the subgenus Oxycoccus of the genus Vaccinium.', user_id, user_id),
('Cucumber', 'Cucumber is a widely-cultivated creeping vine plant in the Cucurbitaceae gourd family that bears usually cylindrical fruits, which are used as vegetables.', user_id, user_id),
('Oat', 'The oat, sometimes called the common oat, is a species of cereal grain grown for its seed, which is known by the same name.', user_id, user_id),
('Citrus', 'Citrus is a genus of flowering trees and shrubs in the rue family, Rutaceae.', user_id, user_id),
('Sugar Cane', 'Sugarcane or sugar cane refers to several species and hybrids of tall perennial grass in the genus Saccharum, tribe Andropogoneae, that are used for sugar.', user_id, user_id),
('Strawberry', 'The garden strawberry is a widely grown hybrid species of the genus Fragaria, collectively known as the strawberries, which are cultivated worldwide.', user_id, user_id),
('Honey', 'A honey bee (also spelled honeybee) is a eusocial flying insect within the genus Apis of the bee clade, all native to Eurasia.', user_id, user_id),
('Pecan', 'The pecan is a species of hickory native to the southern United States and northern Mexico in the region of the Mississippi River.', user_id, user_id),
('Lettuce', 'Lettuce is an annual plant of the daisy family, Asteraceae.', user_id, user_id),
('Cotton', 'Cotton is a soft, fluffy staple fiber that grows in a boll, or protective case, around the seeds of the cotton plants of the genus Gossypium.', user_id, user_id);

END $$;