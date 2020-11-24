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

import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.dao.db.tables.pojos.ProgramObservationLevelEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.ProgramObservationLevelDAO;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProgramObservationLevelService {

    private ProgramObservationLevelDAO programObservationLevelDAO;
    private ProgramDAO programDAO;

    @Inject
    public ProgramObservationLevelService(ProgramObservationLevelDAO programObservationLevelDAO,
                                          ProgramDAO programDAO){
        this.programObservationLevelDAO = programObservationLevelDAO;
        this.programDAO = programDAO;
    }

    public List<ProgramObservationLevel> getByProgramId(UUID programId) throws DoesNotExistException {
        if (!this.programDAO.existsById(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }
        List<ProgramObservationLevelEntity> programLevels = programObservationLevelDAO.fetchByProgramId(programId);
        return programLevels.stream()
                .map(programLevel -> new ProgramObservationLevel(programLevel))
                .collect(Collectors.toList());
    }

    public List<ProgramObservationLevel> createLevels(UUID programId, List<String> levels, AuthenticatedUser actingUser) throws DoesNotExistException {
        List<ProgramObservationLevelEntity> levelEntities = levels.stream().map(level ->
                ProgramObservationLevel.builder()
                        .name(level)
                        .programId(programId)
                        .createdBy(actingUser.getId())
                        .updatedBy(actingUser.getId())
                        .build())
                .collect(Collectors.toList());
        programObservationLevelDAO.insert(levelEntities);
        return getByProgramId(programId);
    }
}
