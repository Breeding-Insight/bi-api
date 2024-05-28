package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PendingTrial implements ExperimentImportEntity<BrAPITrial> {
    ExpUnitContext cache;
    ImportContext importContext;
    @Inject
    TrialService trialService;
    BrAPITrialDAO brapiTrialDAO;
    @Inject
    ExperimentUtilities experimentUtilities;

    public PendingTrial(ExpUnitMiddlewareContext context,
                        TrialService trialService,
                        BrAPITrialDAO brapiTrialDAO,
                        ExperimentUtilities experimentUtilities) {
        this.cache = context.getExpUnitContext();
        this.importContext = context.getImportContext();
        this.trialService = trialService;
        this.brapiTrialDAO = brapiTrialDAO;
        this.experimentUtilities = experimentUtilities;

    }
    @Override
    public List<BrAPITrial> brapiPost(List<BrAPITrial> members) throws ApiException {
        return brapiTrialDAO.createBrAPITrials(members, importContext.getProgram().getId(), importContext.getUpload());
    }
    @Override
    public List<BrAPITrial> brapiRead() throws ApiException {
        return null;
    }
    @Override
    public <U> List<U> brapiPut(List<U> members) throws ApiException, IllegalArgumentException {
        // Check if the input list is of type List<BrAPITrial>
        if (!experimentUtilities.isPopulated(members, BrAPITrial.class)) {
            return new ArrayList<U>();
        }

        List<BrAPITrial> updatedTrials = new ArrayList<>();
        for (U member : members) {
            BrAPITrial trial = (BrAPITrial) member;
            Optional.ofNullable(brapiTrialDAO.updateBrAPITrial(trial.getTrialDbId(), trial, importContext.getProgram().getId())).ifPresent(updatedTrials::add);
        }

        return (List<U>) updatedTrials;
    }
    @Override
    public <U> boolean brapiDelete(List<U> members) throws ApiException {
        // TODO: implement delete for trials on BrapiJavaTestServer
        return false;
    }
    @Override
    public List<BrAPITrial> getBrAPIState(ImportObjectState status) throws ApiException {
        List<String> ids = copyWorkflowMembers(status).stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList());
        return brapiTrialDAO.getTrialsByDbIds(ids, importContext.getProgram());
    }
    @Override
    public List<BrAPITrial> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getTrialByNameNoScope(), BrAPITrial.class, status);
    }

    @Override
    public <U> void updateWorkflowWithDbId(List<U> members) {
        // Check if the input list is of type List<BrAPITrial>
        if (!experimentUtilities.isPopulated(members, BrAPITrial.class)) {
            return;
        }

        // Update the workflow ref by setting the system-generated dbId for each newly created trial
        for (U member : members) {
            BrAPITrial trial = (BrAPITrial) member;
            String createdTrialNameNoScope = Utilities.removeProgramKey(trial.getTrialName(), importContext.getProgram().getKey());
            cache.getTrialByNameNoScope().get(createdTrialNameNoScope).getBrAPIObject().setTrialDbId(trial.getTrialDbId());
        }
    }

}
