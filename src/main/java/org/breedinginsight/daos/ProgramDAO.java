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

package org.breedinginsight.daos;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.APIException;
import org.brapi.client.v2.model.exceptions.HttpException;
import org.brapi.client.v2.model.exceptions.HttpNotFoundException;
import org.brapi.client.v2.modules.core.ProgramsAPI;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.core.model.BrApiProgram;
import org.brapi.v2.core.model.request.ProgramsRequest;
import org.brapi.v2.phenotyping.model.BrApiVariable;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.dao.db.tables.BiUserTable;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.jooq.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.*;

@Singleton
public class ProgramDAO extends ProgramDao {

    @Property(name = "brapi.server.core-url")
    private String defaultBrAPICoreUrl;
    @Property(name = "brapi.server.pheno-url")
    private String defaultBrAPIPhenoUrl;
    @Property(name = "brapi.server.geno-url")
    private String defaultBrAPIGenoUrl;

    private DSLContext dsl;
    private BrAPIProvider brAPIProvider;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public ProgramDAO(Configuration config, DSLContext dsl, BrAPIProvider brAPIProvider) {
        super(config);
        this.dsl = dsl;
        this.brAPIProvider = brAPIProvider;
    }

    public List<Program> get(List<UUID> programIds){
        return getPrograms(programIds);
    }

    public List<Program> get(UUID programId) {
        List<UUID> programList = new ArrayList<>();
        programList.add(programId);
        return getPrograms(programList);
    }

    public List<Program> getFromEntity(List<ProgramEntity> programEntities){
        List<UUID> programList = new ArrayList<>();
        for (ProgramEntity programEntity: programEntities){
            programList.add(programEntity.getId());
        }
        return getPrograms(programList);
    }

    public List<Program> getAll()
    {
        return getPrograms(null);
    }

    private List<Program> getPrograms(List<UUID> programIds){

        BiUserTable createdByUser = BI_USER.as("createdByUser");
        BiUserTable updatedByUser = BI_USER.as("updatedByUser");
        Result<Record> queryResult;
        List<Program> resultPrograms = new ArrayList<>();

        SelectOnConditionStep<Record> query = dsl.select()
                .from(PROGRAM)
                .join(SPECIES).on(PROGRAM.SPECIES_ID.eq(SPECIES.ID))
                .join(createdByUser).on(PROGRAM.CREATED_BY.eq(createdByUser.ID))
                .join(updatedByUser).on(PROGRAM.UPDATED_BY.eq(updatedByUser.ID));

        if (programIds != null){
            queryResult = query
                    .where(PROGRAM.ID.in(programIds))
                    .fetch();
        } else {
            queryResult = query.fetch();
        }

        // Parse the result
        for (Record record: queryResult){
            Program program = Program.parseSQLRecord(record);
            program.setSpecies(Species.parseSQLRecord(record));
            program.setCreatedByUser(User.parseSQLRecord(record, createdByUser));
            program.setUpdatedByUser(User.parseSQLRecord(record, updatedByUser));
            resultPrograms.add(program);
        }

        return resultPrograms;
    }

    public ProgramBrAPIEndpoints getProgramBrAPIEndpoints() {
        return new ProgramBrAPIEndpoints();
    }

    public ProgramBrAPIEndpoints getProgramBrAPIEndpoints(UUID programId) {
        // Just returns defaults for now
        // TODO: in future return urls for given program
        return ProgramBrAPIEndpoints.builder()
                .coreUrl(Optional.of(defaultBrAPICoreUrl))
                .genoUrl(Optional.of(defaultBrAPIGenoUrl))
                .phenoUrl(Optional.of(defaultBrAPIPhenoUrl))
                .build();
    }

    public void createProgramBrAPI(Program program) {

        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(program.getId().toString())
                .referenceSource(referenceSource)
                .build();

        BrApiProgram brApiProgram = BrApiProgram.builder()
                .programName(program.getName())
                .abbreviation(program.getAbbreviation())
                .commonCropName(program.getSpecies().getCommonName())
                .externalReferences(List.of(externalReference))
                .objective(program.getObjective())
                .documentationURL(program.getDocumentationUrl())
                .build();

        // POST programs to each brapi service
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        try {
            List<ProgramsAPI> programsAPIS = brAPIProvider.getAllUniqueProgramsAPI();
            for (ProgramsAPI programsAPI: programsAPIS){
                programsAPI.createProgram(brApiProgram);
            }
        } catch (HttpException | APIException e) {
            throw new InternalServerException(e.getMessage());
        }

    }

    public void updateProgramBrAPI(Program program) {

        ProgramsRequest searchRequest = ProgramsRequest.builder()
                .externalReferenceID(program.getId().toString())
                .externalReferenceSource(referenceSource)
                .build();

        // Program goes in all of the clients
        // TODO: If there is a failure after the first brapi service, roll back all before the failure.
        List<ProgramsAPI> programsAPIS = brAPIProvider.getAllUniqueProgramsAPI();
        for (ProgramsAPI programsAPI: programsAPIS){

            // Get existing brapi program
            List<BrApiProgram> brApiPrograms;
            try {
                brApiPrograms = programsAPI.getPrograms(searchRequest);
            } catch (HttpException | APIException e) {
                throw new HttpServerException("Could not find program in BrAPI service.");
            }

            if (brApiPrograms.size() == 0){
                throw new HttpServerException("Could not find program in BrAPI service.");
            }

            BrApiProgram brApiProgram = brApiPrograms.get(0);

            //TODO: Need to add archived/not archived when available in brapi
            brApiProgram.setProgramName(program.getName());
            brApiProgram.setAbbreviation(program.getAbbreviation());
            brApiProgram.setCommonCropName(program.getSpecies().getCommonName());
            brApiProgram.setObjective(program.getObjective());
            brApiProgram.setDocumentationURL(program.getDocumentationUrl());

            try {
                programsAPI.updateProgram(brApiProgram);
            } catch (HttpException | APIException e) {
                throw new HttpServerException("Could not find program in BrAPI service.");
            }
        }
    }

}

