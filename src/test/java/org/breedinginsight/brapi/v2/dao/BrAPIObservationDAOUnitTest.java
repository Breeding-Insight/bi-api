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
import io.reactivex.functions.Function4;
import lombok.SneakyThrows;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BrAPIObservationDAOUnitTest {

    private static final String BRAPI_PROGRAM_DB_ID = "brapi-program-1";

    private BrAPIObservationDAO observationDAO;
    private BrAPIObservationUnitDAO observationUnitDAO;
    private BrAPIDAOUtil brAPIDAOUtil;
    private Program program;

    @BeforeEach
    void setup() {
        UUID programId = UUID.randomUUID();

        BrAPIProgram brapiProgram = new BrAPIProgram().programDbId(BRAPI_PROGRAM_DB_ID);

        program = new Program();
        program.setId(programId);
        program.setKey("TEST");
        program.setBrapiProgram(brapiProgram);

        ProgramDAO programDAO = mock(ProgramDAO.class);
        observationUnitDAO = mock(BrAPIObservationUnitDAO.class);
        brAPIDAOUtil = mock(BrAPIDAOUtil.class);

        when(programDAO.getCoreClient(programId)).thenReturn(mock(BrAPIClient.class));

        observationDAO = new BrAPIObservationDAO(
                programDAO,
                mock(ImportDAO.class),
                observationUnitDAO,
                brAPIDAOUtil,
                new BrAPIEndpointProvider(),
                1000,
                mock(TraitService.class)
        );
    }

    @Test
    @SneakyThrows
    void getObservationsByTrialDbIdUsesObservationSearch() {
        String trialDbId = "trial-1";
        String observationUnitDbId = "ou-1";

        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit().observationUnitDbId(observationUnitDbId);

        BrAPIObservation observation = new BrAPIObservation().observationDbId("observation-1").observationUnitDbId(observationUnitDbId);

        when(observationUnitDAO.getObservationUnitsForTrialDbIds(program.getId(), List.of(trialDbId))).thenReturn(List.of(observationUnit));

        when(brAPIDAOUtil.search(any(Function.class),any(Function4.class),any(BrAPIObservationSearchRequest.class))).thenReturn(List.of(observation));

        List<BrAPIObservation> result = observationDAO.getObservationsByTrialDbId(List.of(trialDbId), program);

        assertEquals(List.of(observation), result);

        ArgumentCaptor<BrAPIObservationSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class),any(Function4.class),requestCaptor.capture());

        BrAPIObservationSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());

        assertEquals(List.of(observationUnitDbId), request.getObservationUnitDbIds());
    }

    @Test
    @SneakyThrows
    void getObservationsByTrialDbIdReturnsEmptyWhenNoUnitsExist() {
        String trialDbId = "trial-without-units";

        when(observationUnitDAO.getObservationUnitsForTrialDbIds(program.getId(), List.of(trialDbId))).thenReturn(List.of());

        List<BrAPIObservation> result = observationDAO.getObservationsByTrialDbId(List.of(trialDbId), program);

        assertTrue(result.isEmpty());

        verify(brAPIDAOUtil, never()).search(any(Function.class),any(Function4.class),any(BrAPIObservationSearchRequest.class));
    }

    @Test
    @SneakyThrows
    void getObservationsByObservationUnitsUsesObservationSearch() {
        List<String> observationUnitDbIds = List.of("ou-1", "ou-2");

        when(brAPIDAOUtil.search(any(Function.class),any(Function4.class),any(BrAPIObservationSearchRequest.class))).thenReturn(List.of());

        observationDAO.getObservationsByObservationUnits(observationUnitDbIds, program);

        ArgumentCaptor<BrAPIObservationSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class),any(Function4.class),requestCaptor.capture());

        BrAPIObservationSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(observationUnitDbIds, request.getObservationUnitDbIds());
    }

    @Test
    @SneakyThrows
    void getObservationsByObservationUnitsAndVariablesUsesBothFilters() {
        List<String> observationUnitDbIds = List.of("ou-1", "ou-2");
        List<String> observationVariableDbIds = List.of("variable-1", "variable-2");

        when(brAPIDAOUtil.search(any(Function.class), any(Function4.class),any(BrAPIObservationSearchRequest.class))).thenReturn(List.of());

        observationDAO.getObservationsByObservationUnitsAndVariables(observationUnitDbIds, observationVariableDbIds, program);

        ArgumentCaptor<BrAPIObservationSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIObservationSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class),any(Function4.class),requestCaptor.capture());

        BrAPIObservationSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(observationUnitDbIds, request.getObservationUnitDbIds());
        assertEquals(observationVariableDbIds, request.getObservationVariableDbIds());
    }

    @Test
    @SneakyThrows
    void observationSearchIsSkippedForEmptyFilters() {
        assertTrue(observationDAO.getObservationsByTrialDbId(List.of(), program).isEmpty());

        assertTrue(observationDAO.getObservationsByObservationUnits(List.of(), program).isEmpty());

        assertTrue(observationDAO.getObservationsByObservationUnitsAndVariables(List.of("ou-1"), List.of(), program).isEmpty());

        verifyNoInteractions(observationUnitDAO);
        verify(brAPIDAOUtil, never()).search(any(Function.class),any(Function4.class),any(BrAPIObservationSearchRequest.class));
    }
}