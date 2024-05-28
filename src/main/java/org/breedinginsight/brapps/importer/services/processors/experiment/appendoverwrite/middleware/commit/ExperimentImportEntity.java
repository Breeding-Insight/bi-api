package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface ExperimentImportEntity<T> {
    public List<T> brapiPost(List<T> members) throws ApiException;
    public List<T> brapiRead() throws ApiException;
    public <U> List<U> brapiPut(List<U> members) throws ApiException, IllegalArgumentException;
    public <U> boolean brapiDelete(List<U> members) throws ApiException;
    public List<T> getBrAPIState(ImportObjectState status) throws ApiException;
    public List<T> copyWorkflowMembers(ImportObjectState status);
    public <U> void updateWorkflowWithDbId(List<U> members);
}
