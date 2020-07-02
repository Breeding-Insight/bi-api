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
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.dao.db.enums.UploadType;
import org.breedinginsight.daos.ProgramUploadDAO;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.services.constants.MediaType;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

import org.breedinginsight.dao.db.tables.pojos.BatchUploadEntity;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.UnsupportedTypeException;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.breedinginsight.services.validators.TraitValidator;
import org.jooq.JSONB;

import java.io.IOException;
import java.util.*;

@Slf4j
@Singleton
public class ProgramUploadService {

    @Inject
    private ProgramUploadDAO programUploadDao;
    @Inject
    private ProgramService programService;
    @Inject
    private ProgramUserService programUserService;
    @Inject
    private TraitFileParser parser;

    @Inject
    private ObjectMapper objMapper;

    public ProgramUpload updateTraitUpload(UUID programId, CompletedFileUpload file, AuthenticatedUser actingUser)
            throws UnprocessableEntityException, DoesNotExistException, UnsupportedTypeException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        programUserService.getProgramUserbyId(programId, actingUser.getId())
                .orElseThrow(() -> new DoesNotExistException("User not in program"));

        Optional<io.micronaut.http.MediaType> type = file.getContentType();
        io.micronaut.http.MediaType mediaType = type.orElseThrow(() -> new UnsupportedTypeException("File upload must have a mime type"));

        List<Trait> traits;

        if (mediaType.getName().equals(MediaType.CSV)) {
            try {
                traits = parser.parseCsv(file.getInputStream());
            } catch(IOException | ParsingException e) {
                log.error(e.getMessage());
                throw new UnprocessableEntityException("Error parsing csv: " + e.getMessage());
            }
        } else if (mediaType.getName().equals(MediaType.XLS) ||
                   mediaType.getName().equals(MediaType.XLSX)) {
            try {
                traits = parser.parseExcel(file.getInputStream());
            } catch(IOException | ParsingException e) {
                log.error(e.getMessage());
                throw new UnprocessableEntityException("Error parsing excel: " + e.getMessage());
            }
        } else {
            throw new UnsupportedTypeException("Unsupported mime type");
        }

        for (Trait trait : traits) {
            TraitValidator.checkRequiredTraitFields(trait);
            TraitValidator.checkTraitDataConsistency(trait);
        }

        String json = null;
        try {
            json = objMapper.writeValueAsString(traits);
        } catch(JsonProcessingException e) {
            log.error(e.getMessage());
            throw new UnprocessableEntityException("Problem converting traits json");
        }

        // delete any existing records for traits since we only want to allow one at a time
        // if there is some failure in writing the new, the old will be wiped out but that's ok
        // because by making the PUT call the client already expected an overwrite
        programUploadDao.deleteUploads(programId, actingUser.getId(), UploadType.TRAIT);

        // do not autopopulate fields, that will be done on actual trait creation
        BatchUploadEntity uploadEntity = BatchUploadEntity.builder()
                .type(UploadType.TRAIT)
                .programId(programId)
                .userId(actingUser.getId())
                .data(JSONB.valueOf(json))
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();

        // Insert and update
        programUploadDao.insert(uploadEntity);
        return programUploadDao.getUploadById(uploadEntity.getId()).get();
    }

    public Optional<ProgramUpload> getTraitUpload(UUID programId, AuthenticatedUser actingUser) {

        List<ProgramUpload> uploads = programUploadDao.getUploads(programId, actingUser.getId(), UploadType.TRAIT);

        if (uploads.isEmpty()) {
            return Optional.empty();
        } else if (uploads.size() > 1) {
            throw new IllegalStateException("More than one trait upload found, only 1 allowed");
        }

        return Optional.of(uploads.get(0));
    }

    public void deleteTraitUpload(UUID programId, AuthenticatedUser actingUser) throws DoesNotExistException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        programUserService.getProgramUserbyId(programId, actingUser.getId())
                .orElseThrow(() -> new DoesNotExistException("user not in program"));

        programUploadDao.deleteUploads(programId, actingUser.getId(), UploadType.TRAIT);

    }


}
