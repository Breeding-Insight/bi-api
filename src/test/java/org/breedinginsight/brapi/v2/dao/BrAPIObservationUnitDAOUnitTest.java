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

import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import lombok.SneakyThrows;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BrAPIObservationUnitDAOUnitTest {

    private static final String BRAPI_PROGRAM_DB_ID = "brapi-program-1";

    private BrAPIObservationUnitDAO observationUnitDAO;
    private ProgramDAO programDAO;
    private BrAPIDAOUtil brAPIDAOUtil;
    private Program program;

    @BeforeEach
    @SneakyThrows
    void setup() {
        UUID programId = UUID.randomUUID();

        BrAPIProgram brapiProgram = new BrAPIProgram().programDbId(BRAPI_PROGRAM_DB_ID);

        program = new Program();
        program.setId(programId);
        program.setKey("TEST");
        program.setBrapiProgram(brapiProgram);

        programDAO = mock(ProgramDAO.class);
        brAPIDAOUtil = mock(BrAPIDAOUtil.class);
        BrAPIGermplasmService germplasmService = mock(BrAPIGermplasmService.class);

        when(programDAO.get(programId)).thenReturn(List.of(program));
        when(programDAO.getProgramBrAPI(program)).thenReturn(brapiProgram);
        when(programDAO.getCoreClient(programId)).thenReturn(mock(BrAPIClient.class));
        when(germplasmService.getGermplasm(programId)).thenReturn(List.of());

        when(brAPIDAOUtil.search(any(Function.class), any(Function3.class), any(BrAPIObservationUnitSearchRequest.class))).thenReturn(List.of());

        observationUnitDAO = new BrAPIObservationUnitDAO(programDAO, mock(ImportDAO.class),
                brAPIDAOUtil,
                new BrAPIEndpointProvider(), germplasmService, mock(ProgramService.class),
                "test-reference-source",
                1000
        );
    }

    @Test
    @SneakyThrows
    void getObservationUnitsForStudyDbIdUsesBrAPISearch() {
        String studyDbId = "study-1";

        observationUnitDAO.getObservationUnitsForStudyDbId(studyDbId, program);

        ArgumentCaptor<BrAPIObservationUnitSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationUnitSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class), any(Function3.class), requestCaptor.capture());

        BrAPIObservationUnitSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(List.of(studyDbId), request.getStudyDbIds());
    }

    @Test
    @SneakyThrows
    void getObservationUnitsForTrialDbIdsUsesBrAPISearch() {
        List<String> trialDbIds = List.of("trial-1", "trial-2");

        observationUnitDAO.getObservationUnitsForTrialDbIds(program.getId(), trialDbIds);

        ArgumentCaptor<BrAPIObservationUnitSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationUnitSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class), any(Function3.class), requestCaptor.capture());

        BrAPIObservationUnitSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(trialDbIds, request.getTrialDbIds());
    }

    @Test
    @SneakyThrows
    void getObservationUnitsForTrialDbIdUsesBrAPISearch() {
        String trialDbId = "trial-1";

        observationUnitDAO.getObservationUnitsForTrialDbId(program.getId(), trialDbId);

        ArgumentCaptor<BrAPIObservationUnitSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationUnitSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class), any(Function3.class), requestCaptor.capture());

        BrAPIObservationUnitSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(List.of(trialDbId), request.getTrialDbIds());
    }

    @Test
    @SneakyThrows
    void getObservationUnitsForEmptyTrialDbIdsDoesNotSearch() {

        assertTrue(observationUnitDAO.getObservationUnitsForTrialDbIds(program.getId(), List.of()).isEmpty());

        verify(brAPIDAOUtil, never()).search(any(Function.class), any(Function3.class), any(BrAPIObservationUnitSearchRequest.class));
    }
}