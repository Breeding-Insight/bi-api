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
package org.breedinginsight.brapps.importer.base.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.base.daos.ImportDAO;
import org.breedinginsight.brapps.importer.base.model.ImportUpload;
import org.breedinginsight.brapps.importer.base.model.response.ImportPreviewResponse;
import org.jooq.JSONB;
import org.brapi.client.v2.JSON;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class ImportStatusService {

    private ImportDAO importDAO;
    private ObjectMapper objMapper;

    @Inject
    public ImportStatusService(ImportDAO importDAO, ObjectMapper objMapper) {
        this.importDAO = importDAO;
        this.objMapper = objMapper;
    }

    public void updateMessage(ImportUpload upload, String message) {
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void updateStatus(ImportUpload upload, HttpStatus status, String message) {
        upload.getProgress().setMessage(message);
        upload.getProgress().setStatusCode(status.getCode());
        importDAO.update(upload);
    }

    public void finishUpload(ImportUpload upload, ImportPreviewResponse response, String message) {
        JSON config = new JSON();
        String json = config.getGson().toJson(response);
        upload.setMappedData(JSONB.valueOf(json));
        upload.getProgress().setMessage(message);
        upload.getProgress().setStatusCode(HttpStatus.OK.getCode());
        importDAO.update(upload);
    }

    public void updateOk(ImportUpload upload) {
        upload.getProgress().setStatusCode(HttpStatus.OK.getCode());
        importDAO.update(upload);
    }

    public void updateBody(ImportUpload upload, String json) {
        upload.getProgress().setBody(JSONB.valueOf(json));
        importDAO.update(upload);
    }
}
