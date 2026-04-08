package org.breedinginsight.brapi.v2.services;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationLevelDAO;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Slf4j
@Singleton
public class BrAPIObservationLevelService {
    private final BrAPIObservationLevelDAO brAPIObservationLevelDAO;

    @Inject
    public BrAPIObservationLevelService(BrAPIObservationLevelDAO brAPIObservationLevelDAO) {
        this.brAPIObservationLevelDAO = brAPIObservationLevelDAO;
    }

    public List<BrAPIObservationUnitHierarchyLevel> getGlobalLevelNames(Program program) {
        return brAPIObservationLevelDAO.getGlobalObservationLevelNames(program);
    }

    public List<BrAPIObservationUnitHierarchyLevel> getProgrammaticLevelNames(Program program, String brapiProgramDbId) {
        return brAPIObservationLevelDAO.getObservationLevelNamesByProgramId(program, brapiProgramDbId);
    }

    public BrAPIObservationUnitHierarchyLevel createObservationLevel(Program program,
                                                                     String brapiProgramDbId,
                                                                     String levelName,
                                                                     DatasetLevel levelOrder) throws ApiException {
        return brAPIObservationLevelDAO.createLevelName(program, brapiProgramDbId, levelName, levelOrder);
    }

}
