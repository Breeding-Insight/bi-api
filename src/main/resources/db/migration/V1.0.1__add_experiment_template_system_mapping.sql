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
        'ExperimentsTemplateMap',
        'ExperimentImport',
        '[{"id": "726a9f10-4892-4204-9e52-bd2b1d735f65", "value": {"fileFieldName": "Germplasm Name"}, "objectId": "germplasmName"}, {"id": "98774e20-6f89-4d6a-a7c9-f88887228ed6", "value": {"fileFieldName": "Germplasm GID"}, "objectId": "gid"}, {"id": "880ef0c9-4e3e-42d4-9edc-667684a91889", "value": {"fileFieldName": "Test (T) or Check (C )"}, "objectId": "test_or_check"}, {"id": "b693eca7-efcd-4518-a9d3-db0b037a76ee", "value": {"fileFieldName": "Exp Title"}, "objectId": "exp_title"}, {"id": "df340215-db6e-4219-a3b7-119f297b81c3", "value": {"fileFieldName": "Exp Description"}, "objectId": "expDescription"}, {"id": "9ca7cc81-562c-43a7-989a-41da309f603d", "value": {"fileFieldName": "Exp Unit"}, "objectId": "expUnit"}, {"id": "27215777-c8f9-4fe7-a7ac-918d6168b0dd", "value": {"fileFieldName": "Exp Type"}, "objectId": "expType"}, {"id": "19d220e2-dff0-4a3a-ad6e-32f4d8602b5c", "value": {"fileFieldName": "Env"}, "objectId": "env"}, {"id": "861518b9-5c9e-4fe5-b31e-baf16e27155d", "value": {"fileFieldName": "Env Location"}, "objectId": "envLocation"}, {"id": "667355c3-dae1-4a64-94c8-ac2d543bd474", "value": {"fileFieldName": "Env Year"}, "objectId": "envYear"}, {"id": "ad11f2df-c5b4-4a05-8e52-c57625140061", "value": {"fileFieldName": "Exp Unit ID"}, "objectId": "expUnitId"}, {"id": "639b40ec-20f8-4659-8464-6a4be997ac7a", "value": {"fileFieldName": "Exp Replicate #"}, "objectId": "expReplicateNo"}, {"id": "2a62a80f-d8ba-42c4-9997-3b4ac8a965aa", "value": {"fileFieldName": "Exp Block #"}, "objectId": "expBlockNo"}, {"id": "f3e7de69-21ad-4cda-b1cc-a5e1987fb931", "value": {"fileFieldName": "Row"}, "objectId": "row"}, {"id": "251c5bbd-fc4d-4371-a4ce-4e2686f6837e", "value": {"fileFieldName": "Column"}, "objectId": "column"}, {"id": "ce5f61f2-f1de-45a4-8baf-e2471a5d863d", "value": {"fileFieldName": "Exp Trt Factor Name"}, "objectId": "treatmentFactors"}, {"id": "c5b8276f-e777-4385-a80f-5199abe63aac", "objectId": "ObsUnitID"}]',
        '[{"Env": "New Study", "Row": 4, "Column": 5, "Env Year": 2012, "Exp Type": "phenotyping", "Exp Unit": "plot", "Exp Title": "New Trial", "ObsUnitID": "", "Exp Block #": 2, "Exp Unit ID": 3, "Env Location": "New Location", "Germplasm GID": 1, "Germplasm Name": "Test", "Exp Description": "A new trial", "Exp Replicate #": 0, "Exp Trt Factor Name": "Jam application", "Test (T) or Check (C )": true}]',
        false,
        '2022-04-20 20:55:50',
        '2022-04-20 20:55:50',
        user_id,
        user_id
        );

END $$;