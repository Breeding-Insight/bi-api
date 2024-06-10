package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow;

import io.micronaut.context.annotation.Property;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.PendingImportObjectPopulator;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.MULTIPLE_EXP_TITLES;
import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.PREEXISTING_EXPERIMENT_TITLE;

public class CreatePendingImportPopulator implements PendingImportObjectPopulator {

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Override
    public PendingImportObject<BrAPITrial> populateTrial(ImportContext importContext,
                                                         PendingData pendingData,
                                                         ExperimentObservation importRow,
                                                         Supplier<BigInteger> expNextVal)
            throws UnprocessableEntityException {
        
        PendingImportObject<BrAPITrial> trialPio;
        Program program = importContext.getProgram();
        User user = importContext.getUser();
        boolean commit = importContext.isCommit();
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = pendingData.getTrialByNameNoScope();
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();

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
            // NOTE: moved up a level
            //trialByNameNoScope.put(importRow.getExpTitle(), trialPio);
        }

        return trialPio;
    }

    @Override
    public PendingImportObject<BrAPIStudy> populateStudy(ImportContext importContext,
                                                         Supplier<BigInteger> expSeqValue,
                                                         ExperimentObservation importRow,
                                                         Supplier<BigInteger> envNextVal) {
        return null;
    }


}
