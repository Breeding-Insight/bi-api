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

import org.breedinginsight.dao.db.tables.pojos.ProgramOntologyEntity;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.model.ProgramOntology;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProgramOntologyService {

    private ProgramOntologyDAO programOntologyDAO;

    @Inject
    public ProgramOntologyService(ProgramOntologyDAO programOntologyDAO){
        this.programOntologyDAO = programOntologyDAO;
    }

    public Optional<ProgramOntology> getByProgramId(UUID programId){
        List<ProgramOntologyEntity> programOntologyEntities = programOntologyDAO.fetchByProgramId(programId);
        if (programOntologyEntities.size() == 1){
            ProgramOntology programOntology = new ProgramOntology(programOntologyEntities.get(0));
            return Optional.of(programOntology);
        } else {
            return Optional.empty();
        }
    }
}
