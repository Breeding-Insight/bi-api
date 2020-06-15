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
package org.breedinginsight.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramUploadRequest;
import org.breedinginsight.daos.ProgramUploadDAO;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramUploadService {

    @Value("${trait.upload.mime.types}")
    private Set<String> mimeTypes;

    @Inject
    private ProgramUploadDAO programUploadDao;

    public ProgramUpload create(UUID programId, String programUploadRequest, CompletedFileUpload file, AuthenticatedUser actingUser) throws UnprocessableEntityException {

        // TODO: see if we can get micronaut deserialization working so we don't have to do this
        ProgramUploadRequest request;
        ObjectMapper objMapper = new ObjectMapper();
        try {
            request = objMapper.readValue(programUploadRequest, ProgramUploadRequest.class);
        } catch (JsonProcessingException e) {
            // TODO: 400
            throw new UnprocessableEntityException("Problem deserializing body");
        }

        log.info(request.getType());

        Optional<MediaType> type = file.getContentType();
        MediaType mediaType = type.orElseThrow(() -> new UnprocessableEntityException("File upload must have MediaType"));

        log.info(mediaType.getName());
        log.info(mediaType.getType());
        log.info(mediaType.getExtension());
        log.info(file.getFilename());

        if (!mimeTypes.contains(mediaType.getName())) {
            // TODO: 415
            throw new UnprocessableEntityException("Unsupported mime type");
        }

        return new ProgramUpload();
    }


}
