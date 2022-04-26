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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.base.daos.ImportMappingProgramDAO;
import org.breedinginsight.brapps.importer.base.model.config.ImportConfigResponse;
import org.breedinginsight.brapps.importer.base.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.base.daos.ImportMappingDAO;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingEntity;
import org.breedinginsight.dao.db.tables.pojos.ImporterMappingProgramEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.exceptions.*;
import org.jooq.JSONB;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MappingService {

    private ProgramUserService programUserService;
    private ProgramService programService;
    private ImportMappingDAO importMappingDAO;
    private ObjectMapper objectMapper;
    private TemplateManager configManager;
    private ImportMappingProgramDAO importMappingProgramDAO;

    @Inject
    MappingService(ProgramUserService programUserService, ProgramService programService,
                   ImportMappingDAO importMappingDAO, ObjectMapper objectMapper,
                   TemplateManager configManager, ImportMappingProgramDAO importMappingProgramDAO) {
        this.programUserService = programUserService;
        this.programService = programService;
        this.importMappingDAO = importMappingDAO;
        this.objectMapper = objectMapper;
        this.configManager = configManager;
        this.importMappingProgramDAO = importMappingProgramDAO;
    }

    public List<ImportConfigResponse> getAllImportTypeConfigs() {
        return configManager.getAllImportTemplates();
    }

    /**
     * Saves an import template mapping. Matches to the template fields is not enforced.
     * Intended to be called from controller.
     * @param programId
     * @param actingUser
     * @param mappingRequest
     * @return
     * @throws DoesNotExistException
     * @throws AuthorizationException
     * @throws UnsupportedTypeException
     */
    public ImportMapping createMapping(UUID programId, AuthenticatedUser actingUser, ImportMapping mappingRequest) throws AlreadyExistsException, UnprocessableEntityException {

        // TODO: Check import type exists

        // Check a name was passed if saving mapping
        if (mappingRequest.getSaved() && mappingRequest.getName() == null) {
            throw new UnprocessableEntityException("Mapping name required when saving a mapping");
        }

        // Check mapping name not already in use
        List<ImportMapping> matchingMappings = importMappingDAO.getProgramMappingsByName(programId, mappingRequest.getName());
        if (matchingMappings.size() > 0){
            throw new AlreadyExistsException("A mapping with that name already exists");
        }

        // Convert mapping to json for storage
        String json = convertMappingToJson(mappingRequest);

        // Create mapping
        ImporterMappingEntity importMappingEntity = ImporterMappingEntity.builder()
                .name(mappingRequest.getSaved() ? mappingRequest.getName() : null)
                .importerTemplateId(mappingRequest.getImporterTemplateId())
                .mapping(JSONB.valueOf(json))
                .saved(mappingRequest.getSaved())
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();
        importMappingDAO.insert(importMappingEntity);

        // Create program association
        ImporterMappingProgramEntity importerMappingProgramEntity = ImporterMappingProgramEntity.builder()
                .programId(programId)
                .importerMappingId(importMappingEntity.getId())
                .build();
        importMappingProgramDAO.insert(importerMappingProgramEntity);

        return importMappingDAO.getMapping(importMappingEntity.getId()).get();
    }

    /**
     * Updates an import template mapping. Matches to the template fields is not enforced.
     * Intended to be called from controller.
     * @param programId
     * @param actingUser
     * @param mappingId
     * @param mappingRequest
     * @return
     * @throws DoesNotExistException
     * @throws AuthorizationException
     * @throws AlreadyExistsException
     */
    public ImportMapping updateMapping(UUID programId, AuthenticatedUser actingUser, UUID mappingId,
                                       ImportMapping mappingRequest) throws
            DoesNotExistException, AuthorizationException, AlreadyExistsException {

        Program program = validateRequest(programId, actingUser);

        // Get existing mapping
        Optional<ImportMapping> optionalImportMapping = importMappingDAO.getMapping(mappingId);
        if (optionalImportMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that id does not exist");
        }

        // Check it doesn't use a reserved name
        // TODO: Remove when default imports go right to template instead of needing mapping
        List<ImportMapping> importMappings = importMappingDAO.getAllSystemMappings();
        for(ImportMapping systemMapping: importMappings) {
            if (systemMapping.getName().equalsIgnoreCase(mappingRequest.getName())) {
                throw new AlreadyExistsException(String.format("Import name '%s' is a reserved name and cannot be used", mappingRequest.getName()));
            }
        }

        // Check mappings within the given program with the same name
        List<ImportMapping> matchingMappings = importMappingDAO.getProgramMappingsByName(programId, mappingRequest.getName());
        List<ImportMapping> nonSelfMappings = matchingMappings.stream()
                .filter(mapping -> !mapping.getId().equals(mappingId)).collect(Collectors.toList());
        if (nonSelfMappings.size() > 0){
            throw new AlreadyExistsException("A mapping with that name already exists");
        }

        // Save the mapping
        String json = convertMappingToJson(mappingRequest);

        ImporterMappingEntity importMappingEntity = importMappingDAO.fetchOneById(mappingId);
        importMappingEntity.setName(mappingRequest.getName());
        importMappingEntity.setImporterTemplateId(mappingRequest.getImporterTemplateId());
        importMappingEntity.setMapping(JSONB.valueOf(json));
        importMappingDAO.update(importMappingEntity);

        Optional<ImportMapping> updatedMapping = importMappingDAO.getMapping(importMappingEntity.getId());

        return updatedMapping.get();
    }

    /**
     * Converts a mapping object into json for storage in the db.
     * @param mappingRequest
     * @return
     */
    private String convertMappingToJson(ImportMapping mappingRequest) {

        // Save the mapping
        String json = null;
        try {
            json = objectMapper.writeValueAsString(mappingRequest.getMappingConfig());
        } catch(JsonProcessingException e) {
            log.error("Problem converting traits json", e);
            // If we didn't catch this in parsing to the class, this is a server error
            throw new InternalServerException("Problem converting mapping json", e);
        }
        return json;
    }

    public Boolean exists(UUID mappingId) {
        return importMappingDAO.existsById(mappingId);
    }

    public List<ImportMapping> getAllMappings(UUID programId, AuthenticatedUser actingUser)
            throws DoesNotExistException {

        return importMappingDAO.getAllProgramMappings(programId);
    }

    private Program validateRequest(UUID programId, AuthenticatedUser actingUser) throws DoesNotExistException, AuthorizationException{
        Optional<Program> optionalProgram = programService.getById(programId);
        if (!optionalProgram.isPresent())
        {
            throw new DoesNotExistException("Program id does not exist");
        }
        Program program = optionalProgram.get();

        if (!programUserService.existsAndActive(programId, actingUser.getId())) {
            throw new AuthorizationException("User not in program");
        }
        return program;
    }

    public List<ImportMapping> getAllSystemMappings(AuthenticatedUser actingUser) {
        List<ImportMapping> importMappings = importMappingDAO.getAllSystemMappings();
        return importMappings;
    }

    public List<ImportMapping> getSystemMappingByName(AuthenticatedUser actingUser, String name) {
        List<ImportMapping> importMappings = importMappingDAO.getSystemMappingByName(name);
        return importMappings;
    }

    public ImportMapping getMappingDetails(UUID mappingId) throws DoesNotExistException {
        Optional<ImportMapping> optionalMapping = importMappingDAO.getMapping(mappingId);
        if (optionalMapping.isEmpty()) {
            throw new DoesNotExistException("Mapping with that ID does not exist");
        }
        return optionalMapping.get();
    }
}
