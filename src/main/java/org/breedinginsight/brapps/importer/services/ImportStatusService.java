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
package org.breedinginsight.brapps.importer.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
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
        log.debug(message);
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void startUpload(ImportUpload upload, long numberObjects, String message) {
        log.debug(message);
        upload.getProgress().setTotal(numberObjects);
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void finishUpload(ImportUpload upload, long numberObjects, String message) {
        log.debug(message);
        // Update progress to reflect final finished and inProgress counts.
        upload.updateProgress(Math.toIntExact(numberObjects), 0);
        upload.getProgress().setMessage(message);
        upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
        importDAO.update(upload);
    }

    public void updateMappedData(ImportUpload upload, ImportPreviewResponse response, String message) {
        log.debug(message);
        // Save our results to the db
        JSON config = new JSON();
        String json = config.getGson().toJson(response);
        upload.setMappedData(JSONB.valueOf(json));
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void updateOk(ImportUpload upload) {
        upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
        importDAO.update(upload);
    }



}
