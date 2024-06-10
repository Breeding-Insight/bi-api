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
package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingImportObjectData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.PendingImportObjectPopulator;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentSeasonService;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentValidateService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import tech.tablesaw.api.Table;
import org.breedinginsight.model.Trait;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.*;

@Singleton
@Slf4j
public class PopulateNewPendingImportObjectsStep {

    private final ExperimentValidateService experimentValidateService;
    private final ExperimentSeasonService experimentSeasonService;
    private final BrAPIObservationDAO brAPIObservationDAO;
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final DSLContext dsl;

    @Inject
    public PopulateNewPendingImportObjectsStep(ExperimentValidateService experimentValidateService,
                                               ExperimentSeasonService experimentSeasonService,
                                               BrAPIObservationDAO brAPIObservationDAO,
                                               BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                                               DSLContext dsl) {
        this.experimentValidateService = experimentValidateService;
        this.experimentSeasonService = experimentSeasonService;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.dsl = dsl;
    }

    public ProcessedData process(ProcessContext context, ProcessedPhenotypeData phenotypeData) {

        Table data = context.getImportContext().getData();
        ImportUpload upload = context.getImportContext().getUpload();
        ImportContext importContext = context.getImportContext();



        // TODO: implement
        return new ProcessedData();
    }


    // initNew
    // TODO: move to shared service
    private void populatePendingImportObjects(ImportContext importContext,
                                              ProcessedPhenotypeData phenotypeData,
                                              PendingImportObjectPopulator pioPopulator) {

        List<BrAPIImport> importRows = importContext.getImportRows();
        Program program = importContext.getProgram();
        boolean commit = importContext.isCommit();

        Supplier<BigInteger> expNextVal = getNextExperimentSequenceNumber(program);
        Supplier<BigInteger> envNextVal = getNextEnvironmentSequenceNumber(program);

        // NOTE: this was moved to the get existing step and kept in PendingData
        // existingObsByObsHash = fetchExistingObservations(referencedTraits, program);

        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);

            populateIndependentVariablePIOsForRow();

            // ... (Common logic from the original method)

            PendingImportObject<BrAPIObservationUnit> obsUnitPIO = fetchOrCreateObsUnitPIO(program, commit, envSeqValue, importRow);

            processObservations(importContext, phenotypeData, importRow, rowNum, commit, importRow.getEnvYear(), obsUnitPIO, studyPIO);
        }
    }


    /**
     * Returns a Supplier that generates the next experiment sequence number based on the given Program.
     *
     * @param program the Program for which to generate the next experiment sequence number
     * @return a Supplier that generates the next experiment sequence number
     * @throws HttpStatusException if the program is not properly configured for observation unit import
     */
    private Supplier<BigInteger> getNextExperimentSequenceNumber(Program program) {
        String expSequenceName = program.getExpSequence();
        if (expSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the exp sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for observation unit import");
        }
        return () -> dsl.nextval(expSequenceName.toLowerCase());
    }

    /**
     * Retrieves the next environment sequence number for a given program.
     *
     * @param program The program for which to get the next environment sequence number.
     * @return A Supplier representing a function that generates the next environment sequence number.
     * @throws HttpStatusException If the program is not properly configured for environment import.
     */
    private Supplier<BigInteger> getNextEnvironmentSequenceNumber(Program program) {
        String envSequenceName = program.getEnvSequence();
        if (envSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the env sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for environment import");
        }
        return () -> dsl.nextval(envSequenceName.toLowerCase());
    }

    /**
     * Populates independent variable PendingImportObjectData for a given row of import data.
     *
     * @param importContext The import context.
     * @param phenotypeData The processed phenotype data.
     * @param pendingData The pending data.
     * @param importRow The import row.
     * @param expNextVal The supplier for generating experiment next value.
     * @param envNextVal The supplier for generating environment next value.
     * @param pioPopulator The pending import object populator.
     * @return The populated independent variable PendingImportObjectData.
     * @throws MissingRequiredInfoException If any required information is missing.
     * @throws UnprocessableEntityException If the entity is unprocessable.
     * @throws ApiException If there is an API exception.
     */
    private PendingImportObjectData populateIndependentVariablePIOsForRow(ImportContext importContext,
                                                                          ProcessedPhenotypeData phenotypeData,
                                                                          PendingData pendingData,
                                                                          ExperimentObservation importRow,
                                                                          Supplier<BigInteger> expNextVal,
                                                                          Supplier<BigInteger> envNextVal,
                                                                          PendingImportObjectPopulator pioPopulator)
            throws MissingRequiredInfoException, UnprocessableEntityException, ApiException {

        Program program = importContext.getProgram();
        User user = importContext.getUser();
        boolean commit = importContext.isCommit();
        List<Trait> referencedTraits = phenotypeData.getReferencedTraits();

        PendingImportObject<BrAPITrial> trialPIO = null;
        try {
            trialPIO = pioPopulator.populateTrial(importContext, pendingData, importRow, expNextVal);

            // moved up a level
            if (trialPIO.getState() == ImportObjectState.NEW) {
                pendingData.getTrialByNameNoScope().put(importRow.getExpTitle(), trialPIO);
            }
        } catch (UnprocessableEntityException e) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        String expSeqValue = null;
        if (commit) {
            expSeqValue = trialPIO.getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                    .getAsString();
        }

        if (commit) {
            fetchOrCreateDatasetPIO(importRow, program, referencedTraits);
        }

        fetchOrCreateLocationPIO(importRow);

        PendingImportObject<BrAPIStudy> studyPIO = fetchOrCreateStudyPIO(program, commit, expSeqValue, importRow, envNextVal);

        String envSeqValue = null;
        if (commit) {
            envSeqValue = studyPIO.getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.ENVIRONMENT_NUMBER)
                    .getAsString();
        }

        PendingImportObject<BrAPIObservationUnit> obsUnitPIO = fetchOrCreateObsUnitPIO(program, commit, envSeqValue, importRow);

        return PendingImportObjectData.builder()
                .trialPIO(trialPIO)
                .studyPIO(studyPIO)
                .obsUnitPIO(obsUnitPIO)
                .build();
    }

    private void processObservations(ImportContext importContext, ProcessedPhenotypeData phenotypeData, ExperimentObservation importRow,
                                             int rowNum, boolean commit, String studyYear,
                                             PendingImportObject<BrAPIObservationUnit> obsUnitPIO,
                                             PendingImportObject<BrAPIStudy> studyPIO)
            throws UnprocessableEntityException, ApiException, MissingRequiredInfoException {
        Program program = importContext.getProgram();
        User user = importContext.getUser();
        List<Column<?>> phenotypeCols = phenotypeData.getPhenotypeCols();
        Map<String, Column<?>> timeStampColByPheno = phenotypeData.getTimeStampColByPheno();
        List<Trait> referencedTraits = phenotypeData.getReferencedTraits();

        for (Column<?> column : phenotypeCols) {
            // ... (Logic for processing observations)
        }
    }

    // TODO: move common code out
    private void initNewBrapiData(ImportContext importContext, ProcessedPhenotypeData phenotypeData)
            throws UnprocessableEntityException, ApiException, MissingRequiredInfoException {

        Program program = importContext.getProgram();
        List<BrAPIImport> importRows = importContext.getImportRows();
        User user = importContext.getUser();
        boolean commit = importContext.isCommit();
        Map<String, BrAPIObservation> existingObsByObsHash;

        List<Trait> referencedTraits = phenotypeData.getReferencedTraits();
        List<Column<?>> phenotypeCols = phenotypeData.getPhenotypeCols();
        Map<String, Column<?>> timeStampColByPheno = phenotypeData.getTimeStampColByPheno();

        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {


            for (Column<?> column : phenotypeCols) {
                //If associated timestamp column, add
                String dateTimeValue = null;
                if (timeStampColByPheno.containsKey(column.name())) {
                    dateTimeValue = timeStampColByPheno.get(column.name()).getString(rowNum);
                    //If no timestamp, set to midnight
                    if (!dateTimeValue.isBlank() && !validDateTimeValue(dateTimeValue)) {
                        dateTimeValue += MIDNIGHT;
                    }
                }

                // get the study year either referenced from the observation unit or listed explicitly on the import row
                // TODO: handle this different workflows
                String studyYear = hasAllReferenceUnitIds ? studyPIO.getBrAPIObject().getSeasons().get(0) : importRow.getEnvYear();
                String seasonDbId = experimentSeasonService.yearToSeasonDbId(studyYear, program.getId());
                fetchOrCreateObservationPIO(
                        program,
                        user,
                        importRow,
                        column,    //column.name() gets phenotype name
                        rowNum,
                        dateTimeValue,
                        commit,
                        seasonDbId,
                        obsUnitPIO,
                        studyPIO,
                        referencedTraits
                );
            }
        }
    }

    private Map<String, BrAPIObservation> fetchExistingObservations(List<Trait> referencedTraits, Program program) throws ApiException {
        Set<String> ouDbIds = new HashSet<>();
        Set<String> variableDbIds = new HashSet<>();
        Map<String, String> variableNameByDbId = new HashMap<>();
        Map<String, String> ouNameByDbId = new HashMap<>();
        Map<String, String> studyNameByDbId = studyByNameNoScope.values()
                .stream()
                .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                .map(PendingImportObject::getBrAPIObject)
                .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

        studyNameByDbId.keySet().forEach(studyDbId -> {
            try {
                brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyDbId, program).forEach(ou -> {
                    if(StringUtils.isNotBlank(ou.getObservationUnitDbId())) {
                        ouDbIds.add(ou.getObservationUnitDbId());
                    }
                    ouNameByDbId.put(ou.getObservationUnitDbId(), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));
                });
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });

        for (Trait referencedTrait : referencedTraits) {
            variableDbIds.add(referencedTrait.getObservationVariableDbId());
            variableNameByDbId.put(referencedTrait.getObservationVariableDbId(), referencedTrait.getObservationVariableName());
        }

        List<BrAPIObservation> existingObservations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, variableDbIds, program);

        return existingObservations.stream()
                .map(obs -> {
                    String studyName = studyNameByDbId.get(obs.getStudyDbId());
                    String variableName = variableNameByDbId.get(obs.getObservationVariableDbId());
                    String ouName = ouNameByDbId.get(obs.getObservationUnitDbId());

                    String key = getObservationHash(createObservationUnitKey(studyName, ouName), variableName, studyName);

                    return Map.entry(key, obs);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private PendingImportObject<BrAPITrial> fetchOrCreateTrialPIO(
            Program program,
            User user,
            boolean commit,
            ExperimentObservation importRow,
            Supplier<BigInteger> expNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPITrial> trialPio;

        // use the prior trial if observation unit IDs are supplied
        // TODO: handle multiple workflows
        if (hasAllReferenceUnitIds) {
            trialPio = getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES);

            // otherwise create a new trial, but there can be only one allowed
        } else {
            if (trialByNameNoScope.containsKey(importRow.getExpTitle())) {
                PendingImportObject<BrAPIStudy> envPio;
                trialPio = trialByNameNoScope.get(importRow.getExpTitle());
                envPio = studyByNameNoScope.get(importRow.getEnv());

                // creating new units for existing experiments and environments is not possible
                if  (trialPio!=null &&  ImportObjectState.EXISTING==trialPio.getState() &&
                        (StringUtils.isBlank( importRow.getObsUnitID() )) && (envPio!=null && ImportObjectState.EXISTING==envPio.getState() ) ){
                    throw new UnprocessableEntityException(PREEXISTING_EXPERIMENT_TITLE);
                }
            } else if (!trialByNameNoScope.isEmpty()) {
                throw new UnprocessableEntityException(MULTIPLE_EXP_TITLES);
            } else {
                UUID id = UUID.randomUUID();
                String expSeqValue = null;
                if (commit) {
                    expSeqValue = expNextVal.get().toString();
                }
                BrAPITrial newTrial = importRow.constructBrAPITrial(program, user, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
                trialPio = new PendingImportObject<>(ImportObjectState.NEW, newTrial, id);
                trialByNameNoScope.put(importRow.getExpTitle(), trialPio);
            }
        }
        return trialPio;
    }

    private void fetchOrCreateDatasetPIO(ExperimentObservation importRow, Program program, List<Trait> referencedTraits) throws UnprocessableEntityException {
        PendingImportObject<BrAPIListDetails> pio;

        // TODO: multiple workflows
        PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
        String name = String.format("Observation Dataset [%s-%s]",
                program.getKey(),
                trialPIO.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER)
                        .getAsString());
        if (obsVarDatasetByName.containsKey(name)) {
            pio = obsVarDatasetByName.get(name);
        } else {
            UUID id = UUID.randomUUID();
            BrAPIListDetails newDataset = importRow.constructDatasetDetails(
                    name,
                    id,
                    BRAPI_REFERENCE_SOURCE,
                    program,
                    trialPIO.getId().toString());
            pio = new PendingImportObject<BrAPIListDetails>(ImportObjectState.NEW, newDataset, id);
            trialPIO.getBrAPIObject().putAdditionalInfoItem("observationDatasetId", id.toString());
            if (ImportObjectState.EXISTING == trialPIO.getState()) {
                trialPIO.setState(ImportObjectState.MUTATED);
            }
            obsVarDatasetByName.put(name, pio);
        }
        addObsVarsToDatasetDetails(pio, referencedTraits, program);
    }

    private void fetchOrCreateLocationPIO(ExperimentObservation importRow) {
        PendingImportObject<ProgramLocation> pio;
        // TODO: multiple workflows
        String envLocationName = hasAllReferenceUnitIds ?
                pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getLocationName() : importRow.getEnvLocation();
        if (!locationByName.containsKey((importRow.getEnvLocation()))) {
            ProgramLocation newLocation = new ProgramLocation();
            newLocation.setName(envLocationName);
            pio = new PendingImportObject<>(ImportObjectState.NEW, newLocation, UUID.randomUUID());
            this.locationByName.put(envLocationName, pio);
        }
    }

    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
            Program program,
            boolean commit,
            String expSequenceValue,
            ExperimentObservation importRow,
            Supplier<BigInteger> envNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPIStudy> pio;
        // TODO: multiple workflows
        if (hasAllReferenceUnitIds) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            pio = studyByNameNoScope.get(studyName);
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
            pio = studyByNameNoScope.get(importRow.getEnv());
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else {
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies

            // It is assumed that the study has only one season, And that the Years and not
            // the dbId's are stored in getSeason() list.
            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
            if (commit) {
                if(StringUtils.isNotBlank(year)) {
                    String seasonID = this.yearToSeasonDbId(year, program.getId());
                    newStudy.setSeasons(Collections.singletonList(seasonID));
                }
            } else {
                addYearToStudyAdditionalInfo(program, newStudy, year);
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
            this.studyByNameNoScope.put(importRow.getEnv(), pio);
        }
        return pio;
    }

    private PendingImportObject<BrAPIObservationUnit> fetchOrCreateObsUnitPIO(Program program, boolean commit, String envSeqValue, ExperimentObservation importRow) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservationUnit> pio;
        String key = createObservationUnitKey(importRow);
        // TODO: multiple workflows
        if (hasAllReferenceUnitIds) {
            pio = pendingObsUnitByOUId.get(importRow.getObsUnitID());
        } else if (observationUnitByNameNoScope.containsKey(key)) {
            pio = observationUnitByNameNoScope.get(key);
        } else {
            String germplasmName = "";
            if (this.existingGermplasmByGID.get(importRow.getGid()) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid())
                        .getBrAPIObject()
                        .getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = trialByNameNoScope.get(importRow.getExpTitle());;
            UUID trialID = trialPIO.getId();
            UUID datasetId = null;
            if (commit) {
                datasetId = UUID.fromString(trialPIO.getBrAPIObject()
                        .getAdditionalInfo().getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
            }
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, envSeqValue, commit, germplasmName, importRow.getGid(), BRAPI_REFERENCE_SOURCE, trialID, datasetId, studyID, id);

            // check for existing units if this is an existing study
            if (studyPIO.getBrAPIObject().getStudyDbId() != null) {
                List<BrAPIObservationUnit> existingOUs = brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyPIO.getBrAPIObject().getStudyDbId(), program);
                List<BrAPIObservationUnit> matchingOU = existingOUs.stream().filter(ou -> importRow.getExpUnitId().equals(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()))).collect(Collectors.toList());
                if (matchingOU.isEmpty()) {
                    throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
                } else {
                    pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservationUnit) Utilities.formatBrapiObjForDisplay(matchingOU.get(0), BrAPIObservationUnit.class, program));
                }
            } else {
                pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit, id);
            }
            this.observationUnitByNameNoScope.put(key, pio);
        }
        return pio;
    }

    private void fetchOrCreateObservationPIO(Program program,
                                             User user,
                                             ExperimentObservation importRow,
                                             Column column,
                                             Integer rowNum,
                                             String timeStampValue,
                                             boolean commit,
                                             String seasonDbId,
                                             PendingImportObject<BrAPIObservationUnit> obsUnitPIO,
                                             PendingImportObject<BrAPIStudy> studyPIO,
                                             List<Trait> referencedTraits) throws ApiException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservation> pio;
        BrAPIObservation newObservation;
        String variableName = column.name();
        String value = column.getString(rowNum);
        String key;

        // TODO: multiple workflows
        if (hasAllReferenceUnitIds) {
            String unitName = obsUnitPIO.getBrAPIObject().getObservationUnitName();
            String studyName = studyPIO.getBrAPIObject().getStudyName();
            key = getObservationHash(studyName + unitName, variableName, studyName);
        } else {
            key = getImportObservationHash(importRow, variableName);
        }

        if (existingObsByObsHash.containsKey(key)) {
            if (!isObservationMatched(key, value, column, rowNum)){

                // prior observation with updated value
                newObservation = gson.fromJson(gson.toJson(existingObsByObsHash.get(key)), BrAPIObservation.class);
                if (!isValueMatched(key, value)){
                    newObservation.setValue(value);
                } else if (!StringUtils.isBlank(timeStampValue) && !isTimestampMatched(key, timeStampValue)) {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
                    String formattedTimeStampValue = formatter.format(OffsetDateTime.parse(timeStampValue));
                    newObservation.setObservationTimeStamp(OffsetDateTime.parse(formattedTimeStampValue));
                }
                pio = new PendingImportObject<>(ImportObjectState.MUTATED, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(newObservation, BrAPIObservation.class, program));
            } else {

                // prior observation
                pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(existingObsByObsHash.get(key), BrAPIObservation.class, program));
            }

            observationByHash.put(key, pio);
        } else if (!this.observationByHash.containsKey(key)){

            // new observation
            // TODO: multiple workflows
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());

            UUID trialID = trialPIO.getId();
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            newObservation = importRow.constructBrAPIObservation(value, variableName, seasonDbId, obsUnitPIO.getBrAPIObject(), commit, program, user, BRAPI_REFERENCE_SOURCE, trialID, studyID, obsUnitPIO.getId(), id);
            //NOTE: Can't parse invalid timestamp value, so have to skip if invalid.
            // Validation error should be thrown for offending value, but that doesn't happen until later downstream
            if (timeStampValue != null && !timeStampValue.isBlank() && (validDateValue(timeStampValue) || validDateTimeValue(timeStampValue))) {
                newObservation.setObservationTimeStamp(OffsetDateTime.parse(timeStampValue));
            }

            newObservation.setStudyDbId(studyPIO.getId().toString()); //set as the BI ID to facilitate looking up studies when saving new observations

            pio = new PendingImportObject<>(ImportObjectState.NEW, newObservation);
            this.observationByHash.put(key, pio);
        }
    }


}