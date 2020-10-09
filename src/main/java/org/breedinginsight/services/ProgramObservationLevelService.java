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

import org.breedinginsight.dao.db.tables.pojos.ProgramObservationLevelEntity;
import org.breedinginsight.daos.ProgramObservationLevelDAO;
import org.breedinginsight.model.ProgramObservationLevel;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ProgramObservationLevelService {

    private ProgramObservationLevelDAO programObservationLevelDAO;

    @Inject
    public ProgramObservationLevelService(ProgramObservationLevelDAO programObservationLevelDAO){
        this.programObservationLevelDAO = programObservationLevelDAO;
    }

    public List<ProgramObservationLevel> getByProgramId(UUID programId) {
        List<ProgramObservationLevelEntity> programLevels = programObservationLevelDAO.fetchByProgramId(programId);
        return programLevels.stream()
                .map(programLevel -> new ProgramObservationLevel(programLevel))
                .collect(Collectors.toList());
    }
}
