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

package org.breedinginsight.brapi.v2.services;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class BrAPIStudyService {

    private final BrAPIStudyDAO studyDAO;

    @Inject
    public BrAPIStudyService(BrAPIStudyDAO studyDAO) {
        this.studyDAO = studyDAO;
    }

    public List<BrAPIStudy> getStudies(UUID programId) throws ApiException {
        return studyDAO.getStudies(programId);
    }

    public Optional<BrAPIStudy> getStudyByEnvironmentId(Program program, UUID environmentId) throws ApiException {
        return studyDAO.getStudyByEnvironmentId(environmentId, program);
    }

}
