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


-- name: InsertJobs
INSERT INTO public.importer_progress (id, statuscode, message, body, total, finished, in_progress, created_at, updated_at, created_by, updated_by) VALUES ('d02090c3-e40d-45f4-9a63-b8c701c9531b', 202, 'Uploading to brapi service', null, 500, 100, 400, '2022-05-05 21:42:42 +00:00', '2022-05-05 21:42:42 +00:00', (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from bi_user where orcid = '1111-2222-3333-4444'));
INSERT INTO public.importer_progress (id, statuscode, message, body, total, finished, in_progress, created_at, updated_at, created_by, updated_by) VALUES ('066a37e4-c2e7-4d04-9175-7164c7dce70f', 200, 'Completed upload to brapi service', null, 17, 1, 0, '2022-05-17 14:19:33 +00:00', '2022-05-17 14:19:33 +00:00', (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from bi_user where orcid = '1111-2222-3333-4444'));

INSERT INTO public.importer_import (id, program_id, user_id, importer_mapping_id, importer_progress_id, upload_file_name, file_data, modified_data, mapped_data, created_at, updated_at, created_by, updated_by) VALUES ('9aeeace2-7370-4b7d-a574-0ea799a342ef', ?::uuid, (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from importer_mapping where name = 'GermplasmTemplateMap'), '066a37e4-c2e7-4d04-9175-7164c7dce70f', 'germ import 2.xlsx', '[{"Name": "Gnome-1", "Source": "USDA", "Entry No": "1", "External UID": "", "Breeding Method": "UMM", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-2", "Source": "USDA", "Entry No": "2", "External UID": "", "Breeding Method": "UMM", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3", "Source": "Breeding Insight", "Entry No": "3", "External UID": "", "Breeding Method": "BPC", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "2", "Female Parent Entry No": "1"}, {"Name": "Gnome-3-1", "Source": "Breeding Insight", "Entry No": "4", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-2", "Source": "Breeding Insight", "Entry No": "5", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-3", "Source": "Breeding Insight", "Entry No": "6", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-4", "Source": "Breeding Insight", "Entry No": "7", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-5", "Source": "Breeding Insight", "Entry No": "8", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-6", "Source": "Breeding Insight", "Entry No": "9", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-7", "Source": "Breeding Insight", "Entry No": "10", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "11", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "9"}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "12", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "3"}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "13", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "7"}]', null, '{"rows": [{"germplasm": {"id": "1580b261-f504-4a7e-bbc7-e5599f0b2f4b", "state": "NEW", "brAPIObject": {"seedSource": "USDA", "germplasmName": "Gnome-1 [MNTTIM-1]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Unknown maintenance method", "breedingMethodId": "af40db90-64ab-444d-a749-2f857bc90f64", "importEntryNumber": "1"}, "commonCropName": "test", "accessionNumber": "1", "defaultDisplayName": "Gnome-1", "externalReferences": [{"referenceID": "6a1269cb-6e7a-4b87-a353-c681c214bd69", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "7e665eb0-2f7b-4b14-85a4-50f5508de2a8", "state": "NEW", "brAPIObject": {"seedSource": "USDA", "germplasmName": "Gnome-2 [MNTTIM-2]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Unknown maintenance method", "breedingMethodId": "af40db90-64ab-444d-a749-2f857bc90f64", "importEntryNumber": "2"}, "commonCropName": "test", "accessionNumber": "2", "defaultDisplayName": "Gnome-2", "externalReferences": [{"referenceID": "bcc3fab6-04ed-4e2f-b0b0-9e9b247462ef", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "eddb03ff-09c5-481d-9c1c-8fc609d432e5", "state": "NEW", "brAPIObject": {"pedigree": "Gnome-1 [MNTTIM-1]/Gnome-2 [MNTTIM-2]", "seedSource": "Breeding Insight", "germplasmName": "Gnome-3 [MNTTIM-3]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Biparental cross", "breedingMethodId": "aee4d3a3-0df8-422f-b052-d9f0b81e5b4d", "importEntryNumber": "3", "maleParentEntryNo": "2", "femaleParentEntryNo": "1"}, "commonCropName": "test", "accessionNumber": "3", "defaultDisplayName": "Gnome-3", "externalReferences": [{"referenceID": "24c3164e-0b37-44b4-8b18-9118ca9b2949", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "42ff9b3e-3f04-42f3-909b-2aaa158457e5", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-1 [MNTTIM-4]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "4"}, "commonCropName": "test", "accessionNumber": "4", "defaultDisplayName": "Gnome-3-1", "externalReferences": [{"referenceID": "c271722b-488b-41c8-9671-73ac008be522", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "883cef8e-34c9-43f7-8062-d7d80c8a65fd", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-2 [MNTTIM-5]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "5"}, "commonCropName": "test", "accessionNumber": "5", "defaultDisplayName": "Gnome-3-2", "externalReferences": [{"referenceID": "6bb014ce-0c5f-480c-a1e2-9b87a914a733", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "6215c013-2ed9-43c1-a964-15f87024da36", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-3 [MNTTIM-6]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "6"}, "commonCropName": "test", "accessionNumber": "6", "defaultDisplayName": "Gnome-3-3", "externalReferences": [{"referenceID": "988fe16a-6308-4146-9cae-be9d0cd9c295", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "bd62dec0-25d3-4bef-b9f7-5af634f78366", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-4 [MNTTIM-7]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "7"}, "commonCropName": "test", "accessionNumber": "7", "defaultDisplayName": "Gnome-3-4", "externalReferences": [{"referenceID": "28e6482a-2804-4aa8-b98d-6c4a56f8d8d2", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "c0067078-7a6d-4c99-b6ee-ab220feccddd", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-5 [MNTTIM-8]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "8"}, "commonCropName": "test", "accessionNumber": "8", "defaultDisplayName": "Gnome-3-5", "externalReferences": [{"referenceID": "e7c25b55-61f5-4845-9080-d2243171f70b", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "cdb38aac-51fc-4ea7-9b38-8072258ceac5", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-6 [MNTTIM-9]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "9"}, "commonCropName": "test", "accessionNumber": "9", "defaultDisplayName": "Gnome-3-6", "externalReferences": [{"referenceID": "ade6b8ca-b2e0-49cc-ac09-09476b1416e7", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "3f6c7d4b-fca2-4b22-aa6a-aadb347cd625", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-7 [MNTTIM-10]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "10"}, "commonCropName": "test", "accessionNumber": "10", "defaultDisplayName": "Gnome-3-7", "externalReferences": [{"referenceID": "6cd34ea3-86b0-41a2-aef0-57c101bd68fb", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "68a72f81-42b5-4425-8034-eef7d6921928", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3-6 [MNTTIM-9]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-11]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "11", "maleParentEntryNo": "1", "femaleParentEntryNo": "9"}, "commonCropName": "test", "accessionNumber": "11", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "354faf95-682c-4a62-90f4-4f6c9c2d4191", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "25d84084-45a6-4d93-a683-004f95d3dc3e", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3 [MNTTIM-3]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-12]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "12", "maleParentEntryNo": "1", "femaleParentEntryNo": "3"}, "commonCropName": "test", "accessionNumber": "12", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "936a608f-0e2b-4cee-b348-dbbd57cbdf65", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "569fd3a0-1960-45e0-86d4-5157e21e8130", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3-4 [MNTTIM-7]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-13]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "13", "maleParentEntryNo": "1", "femaleParentEntryNo": "7"}, "commonCropName": "test", "accessionNumber": "13", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "4e708554-e088-48ce-a16d-43f1778549fa", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}], "statistics": {"Germplasm": {"newObjectCount": 13}, "Pedigree Connections": {"newObjectCount": 4}}}', '2022-05-17 14:19:33 +00:00', '2022-05-17 14:19:33 +00:00', (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from bi_user where orcid = '1111-2222-3333-4444'));

INSERT INTO public.importer_import (id, program_id, user_id, importer_mapping_id, importer_progress_id, upload_file_name, file_data, modified_data, mapped_data, created_at, updated_at, created_by, updated_by) VALUES ('210c7175-9d7a-454c-b35f-c8aa0ee0a31e', ?::uuid, (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from importer_mapping where name = 'GermplasmTemplateMap'), 'd02090c3-e40d-45f4-9a63-b8c701c9531b', 'germ import 2.xlsx', '[{"Name": "Gnome-1", "Source": "USDA", "Entry No": "1", "External UID": "", "Breeding Method": "UMM", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-2", "Source": "USDA", "Entry No": "2", "External UID": "", "Breeding Method": "UMM", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3", "Source": "Breeding Insight", "Entry No": "3", "External UID": "", "Breeding Method": "BPC", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "2", "Female Parent Entry No": "1"}, {"Name": "Gnome-3-1", "Source": "Breeding Insight", "Entry No": "4", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-2", "Source": "Breeding Insight", "Entry No": "5", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-3", "Source": "Breeding Insight", "Entry No": "6", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-4", "Source": "Breeding Insight", "Entry No": "7", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-5", "Source": "Breeding Insight", "Entry No": "8", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-6", "Source": "Breeding Insight", "Entry No": "9", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-3-7", "Source": "Breeding Insight", "Entry No": "10", "External UID": "", "Breeding Method": "CFV", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "", "Female Parent Entry No": ""}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "11", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "9"}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "12", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "3"}, {"Name": "Gnome-11", "Source": "Theoretical Breeding Institute", "Entry No": "13", "External UID": "", "Breeding Method": "BCR", "Male Parent GID": "", "Female Parent GID": "", "Male Parent Entry No": "1", "Female Parent Entry No": "7"}]', null, '{"rows": [{"germplasm": {"id": "1580b261-f504-4a7e-bbc7-e5599f0b2f4b", "state": "NEW", "brAPIObject": {"seedSource": "USDA", "germplasmName": "Gnome-1 [MNTTIM-1]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Unknown maintenance method", "breedingMethodId": "af40db90-64ab-444d-a749-2f857bc90f64", "importEntryNumber": "1"}, "commonCropName": "test", "accessionNumber": "1", "defaultDisplayName": "Gnome-1", "externalReferences": [{"referenceID": "6a1269cb-6e7a-4b87-a353-c681c214bd69", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "7e665eb0-2f7b-4b14-85a4-50f5508de2a8", "state": "NEW", "brAPIObject": {"seedSource": "USDA", "germplasmName": "Gnome-2 [MNTTIM-2]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Unknown maintenance method", "breedingMethodId": "af40db90-64ab-444d-a749-2f857bc90f64", "importEntryNumber": "2"}, "commonCropName": "test", "accessionNumber": "2", "defaultDisplayName": "Gnome-2", "externalReferences": [{"referenceID": "bcc3fab6-04ed-4e2f-b0b0-9e9b247462ef", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "eddb03ff-09c5-481d-9c1c-8fc609d432e5", "state": "NEW", "brAPIObject": {"pedigree": "Gnome-1 [MNTTIM-1]/Gnome-2 [MNTTIM-2]", "seedSource": "Breeding Insight", "germplasmName": "Gnome-3 [MNTTIM-3]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Biparental cross", "breedingMethodId": "aee4d3a3-0df8-422f-b052-d9f0b81e5b4d", "importEntryNumber": "3", "maleParentEntryNo": "2", "femaleParentEntryNo": "1"}, "commonCropName": "test", "accessionNumber": "3", "defaultDisplayName": "Gnome-3", "externalReferences": [{"referenceID": "24c3164e-0b37-44b4-8b18-9118ca9b2949", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "42ff9b3e-3f04-42f3-909b-2aaa158457e5", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-1 [MNTTIM-4]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "4"}, "commonCropName": "test", "accessionNumber": "4", "defaultDisplayName": "Gnome-3-1", "externalReferences": [{"referenceID": "c271722b-488b-41c8-9671-73ac008be522", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "883cef8e-34c9-43f7-8062-d7d80c8a65fd", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-2 [MNTTIM-5]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "5"}, "commonCropName": "test", "accessionNumber": "5", "defaultDisplayName": "Gnome-3-2", "externalReferences": [{"referenceID": "6bb014ce-0c5f-480c-a1e2-9b87a914a733", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "6215c013-2ed9-43c1-a964-15f87024da36", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-3 [MNTTIM-6]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "6"}, "commonCropName": "test", "accessionNumber": "6", "defaultDisplayName": "Gnome-3-3", "externalReferences": [{"referenceID": "988fe16a-6308-4146-9cae-be9d0cd9c295", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "bd62dec0-25d3-4bef-b9f7-5af634f78366", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-4 [MNTTIM-7]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "7"}, "commonCropName": "test", "accessionNumber": "7", "defaultDisplayName": "Gnome-3-4", "externalReferences": [{"referenceID": "28e6482a-2804-4aa8-b98d-6c4a56f8d8d2", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "c0067078-7a6d-4c99-b6ee-ab220feccddd", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-5 [MNTTIM-8]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "8"}, "commonCropName": "test", "accessionNumber": "8", "defaultDisplayName": "Gnome-3-5", "externalReferences": [{"referenceID": "e7c25b55-61f5-4845-9080-d2243171f70b", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "cdb38aac-51fc-4ea7-9b38-8072258ceac5", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-6 [MNTTIM-9]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "9"}, "commonCropName": "test", "accessionNumber": "9", "defaultDisplayName": "Gnome-3-6", "externalReferences": [{"referenceID": "ade6b8ca-b2e0-49cc-ac09-09476b1416e7", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "3f6c7d4b-fca2-4b22-aa6a-aadb347cd625", "state": "NEW", "brAPIObject": {"seedSource": "Breeding Insight", "germplasmName": "Gnome-3-7 [MNTTIM-10]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Clone formation (Veg)", "breedingMethodId": "48409513-d05d-458a-acf6-a5c501734145", "importEntryNumber": "10"}, "commonCropName": "test", "accessionNumber": "10", "defaultDisplayName": "Gnome-3-7", "externalReferences": [{"referenceID": "6cd34ea3-86b0-41a2-aef0-57c101bd68fb", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "68a72f81-42b5-4425-8034-eef7d6921928", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3-6 [MNTTIM-9]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-11]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "11", "maleParentEntryNo": "1", "femaleParentEntryNo": "9"}, "commonCropName": "test", "accessionNumber": "11", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "354faf95-682c-4a62-90f4-4f6c9c2d4191", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "25d84084-45a6-4d93-a683-004f95d3dc3e", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3 [MNTTIM-3]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-12]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "12", "maleParentEntryNo": "1", "femaleParentEntryNo": "3"}, "commonCropName": "test", "accessionNumber": "12", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "936a608f-0e2b-4cee-b348-dbbd57cbdf65", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}, {"germplasm": {"id": "569fd3a0-1960-45e0-86d4-5157e21e8130", "state": "EXISTING", "brAPIObject": {"pedigree": "Gnome-3-4 [MNTTIM-7]/Gnome-1 [MNTTIM-1]", "seedSource": "Theoretical Breeding Institute", "germplasmName": "Gnome-11 [MNTTIM-13]", "additionalInfo": {"createdBy": {"userId": "55e268e9-cc02-44e6-ad1e-d99ea9770830", "userName": "BI-DEV Admin"}, "createdDate": "17/05/2022 10:19:54", "breedingMethod": "Backcross", "breedingMethodId": "2ac64ec4-19af-4b4a-946f-9b1be0cc1730", "importEntryNumber": "13", "maleParentEntryNo": "1", "femaleParentEntryNo": "7"}, "commonCropName": "test", "accessionNumber": "13", "defaultDisplayName": "Gnome-11", "externalReferences": [{"referenceID": "4e708554-e088-48ce-a16d-43f1778549fa", "referenceSource": "breedinginsight.org"}, {"referenceID": "ee801336-c885-44f1-873f-666ec6e0740a", "referenceSource": "breedinginsight.org/programs"}]}}}], "statistics": {"Germplasm": {"newObjectCount": 13}, "Pedigree Connections": {"newObjectCount": 4}}}', '2022-05-18 14:19:33 +00:00', '2022-05-18 14:19:33 +00:00', (select id from bi_user where orcid = '1111-2222-3333-4444'), (select id from bi_user where orcid = '1111-2222-3333-4444'));




