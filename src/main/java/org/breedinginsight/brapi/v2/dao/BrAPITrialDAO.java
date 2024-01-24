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

package org.breedinginsight.brapi.v2.dao;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrAPITrialDAO {
    List<BrAPITrial> getTrialsByName(List<String> trialNames, Program program) throws ApiException;

    List<BrAPITrial> createBrAPITrials(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload)
            throws ApiException;

    BrAPITrial updateBrAPITrial(String trialDbId, BrAPITrial trial, UUID programId) throws ApiException;

    List<BrAPITrial> getTrials(UUID programId) throws ApiException;

    Optional<BrAPITrial> getTrialById(UUID programId, UUID trialId) throws ApiException, DoesNotExistException;

    Optional<BrAPITrial> getTrialByDbId(String trialDbId, Program program) throws ApiException;

    List<BrAPITrial> getTrialsByDbIds(Collection<String> trialDbIds, Program program) throws ApiException;

    List<BrAPITrial> getTrialsByExperimentIds(Collection<UUID> experimentIds, Program program) throws ApiException;
}
