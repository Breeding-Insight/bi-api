package org.breedinginsight.brapi.v2.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponse;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationLevelDAO;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class BrAPIObservationLevelService {
    private final BrAPIObservationLevelDAO brAPIObservationLevelDAO;
    private final ProgramService programService;


    @Inject
    public BrAPIObservationLevelService(BrAPIObservationLevelDAO brAPIObservationLevelDAO,
                                        ProgramService programService) {
        this.brAPIObservationLevelDAO = brAPIObservationLevelDAO;
        this.programService = programService;
    }

    /**
     * @return Pair[GlobalLevelNames, ProgrammaticLevelNames]
     */
    public Pair<List<BrAPIObservationUnitHierarchyLevel>, List<BrAPIObservationUnitHierarchyLevel>> getGlobalAndProgrammaticLevelNames(Program program, String brapiProgramDbId) {
        List<BrAPIObservationUnitHierarchyLevel> globalLevels = brAPIObservationLevelDAO.getGlobalObservationLevelNames(program);
        List<BrAPIObservationUnitHierarchyLevel> programmaticLevels = brAPIObservationLevelDAO.getObservationLevelNamesByProgramId(program, brapiProgramDbId);


        return new ImmutablePair<>(globalLevels, programmaticLevels);
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
