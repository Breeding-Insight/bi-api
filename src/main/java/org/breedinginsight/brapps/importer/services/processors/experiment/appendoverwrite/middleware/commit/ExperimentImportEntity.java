package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;

import java.util.List;
import java.util.Map;

public interface ExperimentImportEntity<T> {
    public List<T> constructUpdate(PendingData cache);
    public List<T> constructNew(PendingData cache);
    public Map<String,List<T>> getBrapiState();
    public List<T> brapiPost(List<T> members) throws ApiException;
    public List<T> brapiRead() throws ApiException;
    public <U> List<U> brapiPut(List<U> members) throws ApiException;
    public <U> boolean brapiDelete(List<U> members) throws ApiException;
    public List<T> getBrAPIStateMutatedMembers() throws ApiException;
    public List<T> getMutatedBrAPIMembers();

    public List<T> getNewBrAPIMembers();
    public <U> void updateCache(List<U> members);
}
