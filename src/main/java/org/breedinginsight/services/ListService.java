package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.daos.ListDAO;
import org.breedinginsight.model.delta.DeltaListDetails;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.UUID;

@Slf4j
@Singleton
public class ListService {
    private final BrAPIListDAO brAPIListDAO;
    private final ListDAO listDAO;

    @Inject
    public ListService(BrAPIListDAO brAPIListDAO, ListDAO listDAO) {
        this.brAPIListDAO = brAPIListDAO;
        this.listDAO = listDAO;
    }

    public DeltaListDetails getDeltaListDetails(String listDbId, UUID programId) throws ApiException {
        return listDAO.getDeltaListDetailsByDbId(listDbId, programId);
    }

    public void deleteBrAPIList(String listDbId, UUID programId, boolean hardDelete) throws ApiException {
        listDAO.deleteBrAPIList(listDbId, programId, hardDelete);
    }

}
