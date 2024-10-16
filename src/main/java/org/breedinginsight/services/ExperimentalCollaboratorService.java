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

package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.pojos.ExperimentProgramUserRoleEntity;
import org.breedinginsight.daos.ExperimentalCollaboratorDAO;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ExperimentalCollaboratorService {

    private final ExperimentalCollaboratorDAO experimentalCollaboratorDAO;

    @Inject
    public ExperimentalCollaboratorService(ExperimentalCollaboratorDAO experimentalCollaboratorDAO) {
        this.experimentalCollaboratorDAO = experimentalCollaboratorDAO;
    }

    /**
     * Check if an Experimental Collaborator is authorized to access an experiment.
     * @param programUserRoleId the primary key of a program_user_role record representing an experimental collaborator.
     * @param experimentId the BI-assigned UUID of an experiment (stored in xref on the BrAPI trial).
     * @return true if the specified program user is authorized to access the specified experiment.
     */
    public boolean isAuthorized(UUID programUserRoleId, UUID experimentId) {
        return !experimentalCollaboratorDAO
                .fetchByProgramUserIdAndExperimentId(programUserRoleId, experimentId)
                .isEmpty();
    }

    /**
     * Get the BI-assigned UUIDs of all experiments for which an experimental collaborator has authorization.
     * @param programUserRoleId the primary key of a program_user_role record representing an experimental collaborator.
     * @return a list of BI-assigned experiment UUIDs.
     */
    public List<UUID> getAuthorizedExperimentIds(UUID programUserRoleId) {
        // Get the list of experimentIds associated with the programUserRoleId, empty if the programUserRole is active.
        return experimentalCollaboratorDAO.getExperimentIds(programUserRoleId, true);
    }

    public ExperimentProgramUserRoleEntity createExperimentalCollaborator(UUID programUserRoleId, UUID experimentId, UUID createdByUserId) {
        return this.experimentalCollaboratorDAO.create(experimentId, programUserRoleId, createdByUserId);
    }

    public List<ExperimentProgramUserRoleEntity> getExperimentalCollaborators(UUID experimentId) {
        // Get all collaborators for an experiment.
        return this.experimentalCollaboratorDAO.fetchByExperimentId(experimentId);
    }

    public void deleteExperimentalCollaborator(UUID collaboratorId) {
        // Note: collaboratorId is the primary key of the experiment_program_user_role table.
        this.experimentalCollaboratorDAO.deleteById(collaboratorId);
    }
}
