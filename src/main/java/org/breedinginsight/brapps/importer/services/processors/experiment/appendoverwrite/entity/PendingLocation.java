package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.model.ProgramLocation;

import java.util.List;

public class PendingLocation implements ExperimentImportEntity<ProgramLocation> {
    @Override
    public List<ProgramLocation> brapiPost(List<ProgramLocation> members) throws ApiException {
        return null;
    }

    @Override
    public List<ProgramLocation> brapiRead() throws ApiException {
        return null;
    }

    @Override
    public <U> List<U> brapiPut(List<U> members) throws ApiException, IllegalArgumentException {
        return null;
    }

    @Override
    public <U> boolean brapiDelete(List<U> members) throws ApiException {
        return false;
    }

    @Override
    public List<ProgramLocation> getBrAPIState(ImportObjectState status) throws ApiException {
        return null;
    }

    @Override
    public List<ProgramLocation> copyWorkflowMembers(ImportObjectState status) {
        return null;
    }

    @Override
    public <U> void updateWorkflow(List<U> members) {

    }

    @Override
    public <U> void initializeWorkflow(List<U> members) {

    }
}
