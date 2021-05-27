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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.jooq.JSONB;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class ImportStatusService {

    private ImportDAO importDAO;
    private ImportUpload upload;
    private ObjectMapper objMapper;

    @Inject
    public ImportStatusService(ImportDAO importDAO, ObjectMapper objMapper) {
        this.importDAO = importDAO;
        this.objMapper = objMapper;
    }

    public void setUpload(ImportUpload upload) {
        this.upload = upload;
    }

    public ImportUpload getUpload() {
        return this.upload;
    }

    public void updateMessage(String message) {
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void startUpload(long numberObjects, String message) {
        upload.getProgress().setTotal(numberObjects);
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void finishUpload(String message) {
        upload.getProgress().setMessage(message);
        upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
        importDAO.update(upload);
    }

    public void updateMappedData(ImportPreviewResponse response, String message) {
        // Save our results to the db
        String json = null;
        try {
            json = objMapper.writeValueAsString(response);
        } catch(JsonProcessingException e) {
            log.error("Problem converting mapping to json", e);
            // If we didn't catch this error in the validator, this is an unexpected server error.
            throw new InternalServerException("Problem converting mapping to json", e);
        }
        upload.setMappedData(JSONB.valueOf(json));
        upload.getProgress().setMessage(message);
        importDAO.update(upload);
    }

    public void updateOk() {
        upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
        importDAO.update(upload);
    }



}
