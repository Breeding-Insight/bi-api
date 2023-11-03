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

DO
$$
    DECLARE
        user_id UUID;
    BEGIN

        user_id := (SELECT id FROM bi_user WHERE name = 'system');

        INSERT INTO public.importer_mapping (id, name, import_type_id, mapping, file, draft, created_at, updated_at, created_by, updated_by)
        VALUES (uuid_generate_v4(),
                'SampleImport',
                'SampleImport', '[
            {
              "id": "e4038afd-02b9-475a-88b2-4692e331c399",
              "value": {
                "fileFieldName": "PlateID"
              },
              "objectId": "plateId"
            },
            {
              "id": "9488023c-ed28-4357-811e-7c517cbd9f68",
              "value": {
                "fileFieldName": "Row"
              },
              "objectId": "row"
            },
            {
              "id": "54b47821-c868-4fbd-acd6-8b8a64a02230",
              "value": {
                "fileFieldName": "Column"
              },
              "objectId": "column"
            },
            {
              "id": "f9e39005-e8be-4e83-92b8-a459fe296d2f",
              "value": {
                "fileFieldName": "Organism"
              },
              "objectId": "organism"
            },
            {
              "id": "fe37a2a5-00e0-4076-b130-846f19b3defd",
              "value": {
                "fileFieldName": "Species"
              },
              "objectId": "species"
            },
            {
              "id": "e5074e78-b8ba-474e-87df-716a759f0517",
              "value": {
                "fileFieldName": "Germplasm Name"
              },
              "objectId": "germplasmName"
            },
            {
              "id": "15a20991-ee85-49c6-b5c3-5db4e3dca372",
              "value": {
                "fileFieldName": "GID"
              },
              "objectId": "gid"
            },
            {
              "id": "11e2af68-ca45-4164-9bbb-326c68894fec",
              "value": {
                "fileFieldName": "ObsUnitID"
              },
              "objectId": "obsUnitId"
            },
            {
              "id": "e7731381-7b92-42c3-97f0-38fb37208e8d",
              "value": {
                "fileFieldName": "Tissue"
              },
              "objectId": "tissue"
            },
            {
              "id": "de7fed3a-ec44-4139-83cb-c773e09237bd",
              "value": {
                "fileFieldName": "Comments"
              },
              "objectId": "comments"
            }
          ]', '[]',
                false,
                '2023-10-18 02:03:17 +00:00',
                '2023-10-18 02:03:17 +00:00',
                user_id,
                user_id);

    END
$$;