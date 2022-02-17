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
 '[{"id": "f8d43c7e-a618-4c16-8829-3085f7202a67", "mapping": [{"id": "f384837e-ad8d-4dbe-b54e-87b57070bed1", "value": {"fileFieldName": "Name"}, "objectId": "germplasmName"}, {"id": "39628d14-458b-429b-8e66-bb48e0445a83", "value": {"fileFieldName": "Breeding Method"}, "objectId": "breedingMethod"}, {"id": "f1ba63e1-f5e4-433f-a53e-1c2f3e2fa71f", "value": {"fileFieldName": "Source"}, "objectId": "germplasmSource"}, {"id": "f5892565-f888-4596-be82-ab8eeabf37ce", "value": {"fileFieldName": "External UID"}, "objectId": "externalUID"}, {"id": "65507e5d-2d66-4595-8763-e772fe25c870", "value": {"fileFieldName": "Entry No"}, "objectId": "entryNo"}, {"id": "3eae24c1-ca4a-48a2-96d0-3cf4630acd3a", "value": {"fileFieldName": "Female Parent GID"}, "objectId": "femaleParentDBID"}, {"id": "2dbd7262-93a1-44b0-86b7-f5fca290b965", "value": {"fileFieldName": "Male Parent GID"}, "objectId": "maleParentDBID"}, {"id": "6f7f1539-6e8f-4ede-b7d3-3423cc63abec", "value": {"fileFieldName": "Female Parent Entry No"}, "objectId": "femaleParentEntryNo"}, {"id": "25fe9954-bca7-42f1-818a-5f71e242fa1f", "value": {"fileFieldName": "Male Parent Entry No"}, "objectId": "maleParentEntryNo"}, {"id": "15836d5f-8194-40a8-a771-114eaae31eb4", "objectId": "germplasmPUI"}, {"id": "675b6af8-5a17-4146-a503-2e4e1a65d5fa", "objectId": "acquisitionDate"}, {"id": "69a3bd3c-cebc-435c-acdd-0be62dda25ed", "objectId": "countryOfOrigin"}, {"id": "8ab25267-20f2-450e-89ca-21634ff8fadb", "objectId": "collection"}, {"id": "ce1701e2-2f61-4250-8595-9536e3f5ddcf", "objectId": "AdditionalInfo"}, {"id": "3470e9df-a028-45b7-943f-198bc62b6dbe", "objectId": "ExternalReference"}], "objectId": "Germplasm"}]',
 '[{"Name": "Chris-BB_germ_1", "Breeding Method": "BCR", "Source": "Wild"}, {"Name": "Chris-BB_germ_2", "Breeding Method": "BCR", "Source": "Wild"}, {"Name": "Chris-BB_germ_3", "Breeding Method": "BCR", "Source": "Wild"}, {"Name": "Chris-BB_germ_4", "Breeding Method": "BCR", "Source": "Wild"}]',
 false,user_id,user_id);

END $$;


