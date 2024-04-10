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

package org.breedinginsight.brapps.importer.services.processors;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.sample.SampleSubmissionImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.daos.SampleSubmissionDao;
import org.breedinginsight.dao.db.tables.pojos.SampleSubmissionEntity;
import org.breedinginsight.daos.SampleSubmissionDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.breedinginsight.model.User;
import org.breedinginsight.services.SampleSubmissionService;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class SampleSubmissionProcessor implements Processor {

    private static final String MISSING_REQUIRED_DATA = "Missing required data";
    private static final String MISSING_GERM_ASSOCIATION = "One of GID or ObsUnitID is required";
    private static final String UNKNOWN_OBS_UNIT_ID = "Unknown ObsUnitID";
    private static final String UNKNOWN_GID = "Unknown germplasm GID";
    private static final String INVALID_COLUMN = "Column must be a number between 1 and 12";
    private static final String INVALID_ROW = "Row must be a letter between A and H";
    private static final String MULTIPLE_SAMPLES_SINGLE_WELL = "The sample in row %d is already in row: %s, column: %d";
    private final String referenceSource;
    private final BrAPIGermplasmDAO germplasmDAO;
    private final BrAPIObservationUnitDAO observationUnitDAO;
    private final SampleSubmissionService sampleSubmissionService;
    private SampleSubmission submission;
    private Map<String, BrAPIGermplasm> germplasmByGID = new HashMap<>();
    private Map<String, BrAPIGermplasm> germplasmByDbId = new HashMap<>();
    private Map<String, BrAPIObservationUnit> observationUnitsById = new HashMap<>();
    private Map<String, PendingImportObject<BrAPIPlate>> plateById = new HashMap<>();
    private Map<String, int[][]> plateLayouts = new HashMap<>();

    @Inject
    public SampleSubmissionProcessor(@Property(name = "brapi.server.reference-source") String referenceSource,
                                     BrAPIGermplasmDAO germplasmDAO,
                                     BrAPIObservationUnitDAO observationUnitDAO,
                                     SampleSubmissionService sampleSubmissionService) {
        this.referenceSource = referenceSource;
        this.germplasmDAO = germplasmDAO;
        this.observationUnitDAO = observationUnitDAO;
        this.sampleSubmissionService = sampleSubmissionService;
    }

    @Override
    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) throws ValidatorException, ApiException {
        Set<String> gids = importRows.stream()
                                     .filter((row -> StringUtils.isNotBlank(((SampleSubmissionImport) row).getGid())))
                                     .map(row -> ((SampleSubmissionImport) row).getGid())
                                     .collect(Collectors.toSet());

        List<BrAPIGermplasm> germplasm = germplasmDAO.getGermplasm(program.getId());

        List<String> obsUnitIds = importRows.stream()
                                            .filter((row -> StringUtils.isNotBlank(((SampleSubmissionImport) row).getObsUnitId())))
                                            .map(row -> ((SampleSubmissionImport) row).getObsUnitId())
                                            .distinct()
                                            .collect(Collectors.toList());

        List<BrAPIObservationUnit> observationUnits = observationUnitDAO.getObservationUnitsById(obsUnitIds, program);
        Set<String> germDbIds = new HashSet<>();
        String ouRefSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS);
        observationUnits.forEach(ou -> {
            observationUnitsById.put(Utilities.getExternalReference(ou.getExternalReferences(), ouRefSource)
                                              .get()
                                              .getReferenceId(), ou);
            germDbIds.add(ou.getGermplasmDbId());
        });
        germplasm.stream()
                 .filter(germ -> gids.contains(germ.getAccessionNumber()) || germDbIds.contains(germ.getGermplasmDbId()))
                 .forEach(germ -> {
                     germplasmByGID.put(germ.getAccessionNumber(), germ);
                     germplasmByDbId.put(germ.getGermplasmDbId(), germ);
                 });
    }

    @Override
    public Map<String, ImportPreviewStatistics> process(ImportUpload upload,
                                                        List<BrAPIImport> importRows,
                                                        Map<Integer, PendingImport> mappedBrAPIImport,
                                                        Table data,
                                                        Program program,
                                                        User user,
                                                        boolean commit) throws ValidatorException, MissingRequiredInfoException, ApiException {
        ValidationErrors validationErrors = new ValidationErrors();

        String submissionId = null;
        if (commit) {
            submissionId = UUID.randomUUID()
                               .toString();
            SampleSubmissionImport row = (SampleSubmissionImport) importRows.get(0);
            submission = SampleSubmission.builder()
                    .id(UUID.fromString(submissionId))
                    .name(row.getSubmissionName())
                    .createdBy(user.getId())
                    .build();
        }

        for (int i = 0; i < importRows.size(); i++) {
            int rowNum = i + 2; //accounts for column header and 0-index of i
            SampleSubmissionImport row = (SampleSubmissionImport) importRows.get(i);

            if (validRow(row, rowNum, validationErrors)) {
                PendingImport pendingImport = new PendingImport();
                PendingImportObject<BrAPIPlate> plate = plateById.getOrDefault(row.getPlateId(), new PendingImportObject<>(ImportObjectState.NEW, row.constructBrAPIPlate(commit, program, user, referenceSource, submissionId)));
                pendingImport.setPlate(plate);
                plateById.putIfAbsent(plate.getBrAPIObject()
                                           .getPlateName(), plate);

                BrAPIGermplasm germ;
                if (StringUtils.isNotBlank(row.getObsUnitId())) {
                    germ = germplasmByDbId.get(observationUnitsById.get(row.getObsUnitId())
                                                                   .getGermplasmDbId());
                } else {
                    germ = germplasmByGID.get(row.getGid());
                }
                pendingImport.setSample(new PendingImportObject<>(ImportObjectState.NEW,
                                                                  row.constructBrAPISample(commit, program, user, plate.getBrAPIObject(), referenceSource, submissionId, germ, observationUnitsById.get(row.getObsUnitId()))));
                mappedBrAPIImport.put(rowNum, pendingImport);
            }
        }

        if (validationErrors.hasErrors()) {
            throw new ValidatorException(validationErrors);
        }

        return Map.of("Plates",
                      ImportPreviewStatistics.builder()
                                             .newObjectCount(plateById.size())
                                             .build(),
                      "Samples",
                      ImportPreviewStatistics.builder()
                                             .newObjectCount(importRows.size())
                                             .build());
    }

    private boolean validRow(SampleSubmissionImport row, int rowNum, ValidationErrors validationErrors) {
        int numErrorsBefore = validationErrors.getRowErrors().size();
        if (StringUtils.isBlank(row.getPlateId())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.PLATE_ID, MISSING_REQUIRED_DATA, HttpStatus.UNPROCESSABLE_ENTITY));
        }
        int plateRow = -1;
        if (StringUtils.isBlank(row.getRow())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.ROW, MISSING_REQUIRED_DATA, HttpStatus.UNPROCESSABLE_ENTITY));
        } else if (row.getRow().length() > 1
                || row.getRow().toUpperCase().charAt(0) - 'A' < 0
                || row.getRow().toUpperCase().charAt(0) - 'H' > 0) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.ROW, INVALID_ROW, HttpStatus.UNPROCESSABLE_ENTITY));
        } else {
            plateRow = row.getRow().toUpperCase().charAt(0) - 'A';
        }
        int plateCol = -1;
        if (StringUtils.isBlank(row.getColumn())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.COLUMN, MISSING_REQUIRED_DATA, HttpStatus.UNPROCESSABLE_ENTITY));
        } else {
            try {
                plateCol = Integer.parseInt(row.getColumn());
                if (plateCol < 1 || plateCol > 12) {
                    validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.COLUMN, INVALID_COLUMN, HttpStatus.UNPROCESSABLE_ENTITY));
                    plateCol = -1;
                }
            } catch (NumberFormatException e) {
                validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.COLUMN, INVALID_COLUMN, HttpStatus.UNPROCESSABLE_ENTITY));
            }
        }
        if (StringUtils.isBlank(row.getOrganism())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.ORGANISM, MISSING_REQUIRED_DATA, HttpStatus.UNPROCESSABLE_ENTITY));
        }
        if (StringUtils.isBlank(row.getGid()) && StringUtils.isBlank(row.getObsUnitId())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.GERMPLASM_GID, MISSING_GERM_ASSOCIATION, HttpStatus.UNPROCESSABLE_ENTITY));
        } else if (StringUtils.isNotBlank(row.getObsUnitId()) && !observationUnitsById.containsKey(row.getObsUnitId())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.OBS_UNIT_ID, UNKNOWN_OBS_UNIT_ID, HttpStatus.UNPROCESSABLE_ENTITY));
        } else if (StringUtils.isNotBlank(row.getGid()) && !germplasmByGID.containsKey(row.getGid())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.GERMPLASM_GID, UNKNOWN_GID, HttpStatus.UNPROCESSABLE_ENTITY));
        }
        if (StringUtils.isBlank(row.getTissue())) {
            validationErrors.addError(rowNum, new ValidationError(SampleSubmissionImport.Columns.TISSUE, MISSING_REQUIRED_DATA, HttpStatus.UNPROCESSABLE_ENTITY));
        }

        if (plateRow > -1 && plateCol > -1) {
            int[][] plateLayout = plateLayouts.getOrDefault(row.getPlateId(), new int[9][13]);
            if (plateLayout[plateRow][plateCol] > 0) {
                validationErrors.addError(rowNum,
                                          new ValidationError(SampleSubmissionImport.Columns.ROW + "/" + SampleSubmissionImport.Columns.COLUMN,
                                                              String.format(MULTIPLE_SAMPLES_SINGLE_WELL, plateLayout[plateRow][plateCol], Character.toString('A' + plateRow), plateCol),
                                                              HttpStatus.UNPROCESSABLE_ENTITY));
            } else {
                plateLayout[plateRow][plateCol] = rowNum;
                plateLayouts.put(row.getPlateId(), plateLayout);
            }
        }

        return numErrorsBefore == validationErrors.getRowErrors()
                                                  .size();
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {

    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) throws ValidatorException {
        List<BrAPIPlate> platesToSave = plateById.values().stream().map(PendingImportObject::getBrAPIObject).collect(Collectors.toList());
        List<BrAPISample> samplesToSave = mappedBrAPIImport.values().stream().map(row -> row.getSample().getBrAPIObject()).collect(Collectors.toList());

        submission.setPlates(platesToSave);
        submission.setSamples(samplesToSave);

        try {
            sampleSubmissionService.createSubmission(submission, program, upload);
        } catch (ApiException e) {
            log.error("Error saving sample submission import: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException("Error saving sample submission import", e);
        } catch (Exception e) {
            log.error("Error saving sample submission import", e);
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "SampleSubmission";
    }
}
