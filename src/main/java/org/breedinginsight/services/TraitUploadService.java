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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.tika.mime.MediaType;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.enums.UploadType;
import org.breedinginsight.daos.ProgramUploadDAO;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.model.Scale;
import org.breedinginsight.services.constants.SupportedMediaType;
import org.breedinginsight.services.exceptions.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.breedinginsight.dao.db.tables.pojos.BatchUploadEntity;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.breedinginsight.services.validators.TraitFileValidatorError;
import org.breedinginsight.services.validators.TraitValidatorService;
import org.jooq.JSONB;

import java.util.*;

@Slf4j
@Singleton
public class TraitUploadService {

    private ProgramUploadDAO programUploadDao;
    private ProgramService programService;
    private ProgramUserService programUserService;
    private TraitFileParser parser;
    private TraitValidatorService traitValidator;
    private TraitFileValidatorError traitValidatorError;
    private TraitService traitService;
    private MimeTypeParser mimeTypeParser;

    @Inject
    public TraitUploadService(ProgramUploadDAO programUploadDAO, ProgramService programService, ProgramUserService programUserService,
                              TraitFileParser traitFileParser, TraitValidatorService traitValidator, TraitFileValidatorError traitFileValidatorError,
                              TraitService traitService, MimeTypeParser mimeTypeParser){
        this.programUploadDao = programUploadDAO;
        this.programService = programService;
        this.programUserService = programUserService;
        this.parser = traitFileParser;
        this.traitValidator = traitValidator;
        this.traitValidatorError = traitFileValidatorError;
        this.traitService = traitService;
        this.mimeTypeParser = mimeTypeParser;
    }
    @Inject
    private ObjectMapper objMapper;

    public ProgramUpload updateTraitUpload(UUID programId, CompletedFileUpload file, AuthenticatedUser actingUser)
            throws DoesNotExistException, UnsupportedTypeException, AuthorizationException, ValidatorException, HttpStatusException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }

        MediaType mediaType;
        try {
            mediaType = mimeTypeParser.getMediaType(file);
        } catch (IOException e){
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Could not determine file type");
        }

        List<Trait> traits;

        if (mediaType.toString().equals(SupportedMediaType.CSV)) {
            try {
                traits = parser.parseCsv(new BOMInputStream(file.getInputStream(), false));
            } catch(IOException | ParsingException e) {
                log.error(e.getMessage());
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Error parsing csv: " + e.getMessage());
            }
        } else if (mediaType.toString().equals(SupportedMediaType.XLS) ||
                   mediaType.toString().equals(SupportedMediaType.XLSX) ||
                   mediaType.toString().equals(SupportedMediaType.XLSB)) {
            try {
                traits = parser.parseExcel(new BOMInputStream(file.getInputStream(), false));
            } catch(IOException | ParsingException e) {
                log.error(e.getMessage());
                throw new HttpStatusException( HttpStatus.BAD_REQUEST, e.getMessage() );
            }
        } else {
            throw new UnsupportedTypeException("Unsupported mime type");
        }

        // Preprocess traits
        traitService.preprocessTraits(traits);

        ValidationErrors validationErrors = new ValidationErrors();
        traits = this.markScaleNames(traits);
        Optional<ValidationErrors> optionalValidationErrors = traitValidator.checkAllTraitValidations(traits, traitValidatorError);
        if (optionalValidationErrors.isPresent()){
            validationErrors.merge(optionalValidationErrors.get());
        }

        if (validationErrors.hasErrors()){
            throw new ValidatorException(validationErrors);
        }

        traitService.assignTraitsProgramObservationLevel(traits, programId);

        traits = this.markDups(traits, programId);

        String json = null;
        try {
            json = objMapper.writeValueAsString(traits);
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this error in the validator, this is an unexpected server error.
            throw new InternalServerException("Problem converting traits json", e);
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

        ProgramUpload<Trait> programUpload = programUploadDao.getUploadById(uploadEntity.getId()).get();
        programUpload.setParsedData(parseUpload(programUpload));
        return programUpload;
    }
    private List<Trait> markDups(List<Trait> traits, UUID programId){
        List<Trait> dupTraitsByName = this.traitValidator.checkDuplicateTraitsExistingByName(programId, traits);

        for(Trait trait: traits){
            boolean isDup = false;

            for(Trait dupTrait : dupTraitsByName){
                if(dupTrait.getObservationVariableName().equals(trait.getObservationVariableName())){
                    isDup = true;
                    break;
                }
            }

            trait.setIsDup(isDup);
        }

        return traits;
    }

    private List<Trait> markScaleNames(List<Trait> traits){
        for(Trait trait: traits){
            Scale scale = trait.getScale();
            if(scale != null){
                scale.setScaleName( calcDefaultScaleName(trait) );
            }
        }
        return traits;
    }

    private String calcDefaultScaleName(Trait trait){
        Scale scale = trait.getScale();
        if(scale != null){
            String scaleName = scale.getScaleName();
            // if scaleName already exist, use it
            if(scaleName != null) {
                return scaleName;
            }
            else {
                DataType datatype = scale.getDataType();
                if( datatype != null ) {
                    // if the dataType is not Numerical, use the dataType value
                    if(datatype!=DataType.NUMERICAL){
                        String defaultValue = datatype.getLiteral();
                        //Make Title Case
                        String titleCase =
                                defaultValue.substring(0, 1).toUpperCase() +
                                        defaultValue.substring(1).toLowerCase();
                        return titleCase;
                    }
                }
            }
        }
        return null;
    }
    
    public Optional<ProgramUpload<Trait>> getTraitUpload(UUID programId, AuthenticatedUser actingUser) {

        List<ProgramUpload> uploads = programUploadDao.getUploads(programId, actingUser.getId(), UploadType.TRAIT);

        if (uploads.isEmpty()) {
            return Optional.empty();
        } else if (uploads.size() > 1) {
            throw new IllegalStateException("More than one trait upload found, only 1 allowed");
        }

        ProgramUpload programUpload = uploads.get(0);
        programUpload.setParsedData(parseUpload(programUpload));
        return Optional.of(programUpload);
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

    private List<Trait> parseUpload(ProgramUpload<Trait> programUpload) {

        try {
            Trait[] traits = programUpload.getDataJson();
            return Arrays.asList(traits);
        } catch (JsonProcessingException e) {
            throw new HttpServerException("Unable to parse traits json");
        }

    }

    public void confirmUpload(UUID programId, UUID traitUploadId, AuthenticatedUser actingUser)
            throws DoesNotExistException, ValidatorException {

        Optional<ProgramUpload<Trait>> upload = getTraitUpload(programId, actingUser);
        if (upload.isPresent()){
            if (upload.get().getId().equals(traitUploadId)) {
                List<Trait> traits = new ArrayList<>(upload.get().getParsedData());
                traitService.createTraits(programId, traits, actingUser, false);
                deleteTraitUpload(programId, actingUser);
                return;
            }
        }

        throw new DoesNotExistException("Upload does not exist");
    }
}
