-- name: CopyrightNotice
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

-- name: InsertSystemImport
DO $$
DECLARE
    user_id UUID;
BEGIN

user_id := (SELECT id from bi_user where name = 'system');

INSERT INTO public.importer_mapping (name,import_type_id,mapping,file,draft,created_by,updated_by) values
('GermplasmTest','GermplasmImport',
 '[{"id": "1c807017-a635-4e12-bda2-e0b64c2e020e", "mapping": [{"id": "67688c13-6828-47e2-9bce-d119429359ff", "value": {"fileFieldName": "Germplasm Name"}, "objectId": "germplasmName"}, {"id": "427e8424-45ed-43a3-8136-dca900bd122e", "objectId": "femaleParent"}, {"id": "b7c87c71-4c08-4783-9a63-12c4f91be527", "objectId": "maleParent"}, {"id": "a1e31312-1cec-4358-ac88-2c03b0afb077", "objectId": "germplasmPUI"}, {"id": "949c1a5b-e82b-43a6-be65-506e79d5553d", "objectId": "accessionNumber"}, {"id": "3f054559-1a64-460f-9c07-e146b426837c", "objectId": "acquisitionDate"}, {"id": "e739b961-d0c1-46d3-8712-4b38e8142e28", "objectId": "countryOfOrigin"}, {"id": "3fb7ebed-4e58-4d5b-b518-e9925c9d517e", "objectId": "collection"}, {"id": "091f5b57-a2b3-45b1-9b90-59e7b6588c64", "objectId": "AdditionalInfo"}, {"id": "f31120c0-7715-46fb-85c4-e4e643de931e", "objectId": "ExternalReference"}], "objectId": "Germplasm"}]','[{"Family": "Chris_Family_1", "Male Parent": "Chris-MaleParent_1", "Female Parent": "Chris_FemaleParent_1", "Germplasm Name": "Chris-BB_germ_1"}, {"Family": "Chris_Family_1", "Male Parent": "Chris-MaleParent_2", "Female Parent": "Chris_FemaleParent_1", "Germplasm Name": "Chris-BB_germ_2"}, {"Family": "Chris_Family_2", "Male Parent": "Chris-MaleParent_3", "Female Parent": "Chris_FemaleParent_2", "Germplasm Name": "Chris-BB_germ_3"}, {"Family": "Chris_Family_2", "Male Parent": "Chris-MaleParent_4", "Female Parent": "Chris_FemaleParent_2", "Germplasm Name": "Chris-BB_germ_4"}]',
 false,user_id,user_id);

END $$;


