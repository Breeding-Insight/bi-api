package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.breedinginsight.daos.ExperimentProgramUserRoleDAO;

import javax.inject.Inject;
import java.util.*;

@Slf4j
public class ExperimentProgramUserRoleService {

    private final ExperimentProgramUserRoleDAO experimentProgramUserRoleDAO;

    @Inject
    public ExperimentProgramUserRoleService(ExperimentProgramUserRoleDAO experimentProgramUserRoleDAO) {
        this.experimentProgramUserRoleDAO = experimentProgramUserRoleDAO;
    }

    public List<ExperimentProgramUserRoleEntity> getExperimentIdsForProgramUserRole(UUID programUserRoleId) {
        return experimentProgramUserRoleDAO.getProgramUserRoleExperimentIds(programUserRoleId);
    }

    public ExperimentProgramUserRoleEntity createExperimentProgramUserRole(UUID experimentId, UUID programUserRoleId, UUID userId) {
        return experimentProgramUserRoleDAO.createExperimentProgramUserRole(experimentId, programUserRoleId, userId);
    }

}
