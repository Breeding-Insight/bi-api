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
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.FetchFunction;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BrAPIStudyDAOUnitTest {

    private static final String REFERENCE_SOURCE = "breedinginsight.org";

    private BrAPIStudyDAO studyDAO;
    private ProgramDAO programDAO;
    private BrAPIDAOUtil brAPIDAOUtil;
    private ProgramCache<BrAPIStudy> programCache;
    private Program program;
    private UUID programId;
    private UUID environmentId;

    @BeforeEach
    @SneakyThrows
    void setup() {
        programId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        program = new Program();
        program.setId(programId);
        program.setKey("TEST");
        BrAPIProgram brapiProgram = new BrAPIProgram()
                .programDbId("brapi-program-1");
        program.setBrapiProgram(brapiProgram);

        programDAO = mock(ProgramDAO.class);
        brAPIDAOUtil = mock(BrAPIDAOUtil.class);
        ProgramCacheProvider programCacheProvider = mock(ProgramCacheProvider.class);
        programCache = mock(ProgramCache.class);

        when(programCacheProvider.getProgramCache(any(FetchFunction.class), eq(BrAPIStudy.class)))
                .thenReturn(programCache);
        when(programDAO.get(programId)).thenReturn(List.of(program));
        when(programDAO.getCoreClient(programId)).thenReturn(mock(BrAPIClient.class));
        when(programDAO.getProgramBrAPI(program)).thenReturn(brapiProgram);

        studyDAO = new BrAPIStudyDAO(
                programDAO,
                mock(ImportDAO.class),
                brAPIDAOUtil,
                new BrAPIEndpointProvider(),
                programCacheProvider
        );

        Field referenceSource = BrAPIStudyDAO.class.getDeclaredField("referenceSource");
        referenceSource.setAccessible(true);
        referenceSource.set(studyDAO, REFERENCE_SOURCE);

        Field brapiMaxPageSize = BrAPIStudyDAO.class.getDeclaredField("brapiMaxPageSize");
        brapiMaxPageSize.setAccessible(true);
        brapiMaxPageSize.set(studyDAO, 1000);
    }

    @Test
    @SneakyThrows
    void getStudiesUsesDirectBrAPIGetInsteadOfProgramCache() {
        BrAPIStudy study = study(environmentId, "Env1 [TEST-1]");

        when(brAPIDAOUtil.get(any(Function.class), any(StudyQueryParams.class)))
                .thenReturn(List.of(study));

        List<BrAPIStudy> result = studyDAO.getStudies(programId);

        assertEquals(1, result.size());
        assertEquals("Env1", result.get(0).getStudyName());
        ArgumentCaptor<StudyQueryParams> queryParamsCaptor = ArgumentCaptor.forClass(StudyQueryParams.class);
        verify(brAPIDAOUtil).get(any(Function.class), queryParamsCaptor.capture());
        assertEquals("brapi-program-1", queryParamsCaptor.getValue().programDbId());
        assertEquals(0, queryParamsCaptor.getValue().page());
        assertEquals(1000, queryParamsCaptor.getValue().pageSize());
        verify(programCache, never()).get(any(UUID.class));
    }

    @Test
    @SneakyThrows
    void getStudiesByStudyDbIdUsesDirectBrAPISearch() {
        String studyDbId = UUID.randomUUID().toString();
        BrAPIStudy study = new BrAPIStudy().studyDbId(studyDbId).studyName("Env1");

        when(brAPIDAOUtil.search(any(Function.class), any(Function3.class), any(BrAPIStudySearchRequest.class)))
                .thenReturn(List.of(study));

        List<BrAPIStudy> result = studyDAO.getStudiesByStudyDbId(
                        List.of(studyDbId), program);

        assertEquals(1, result.size());
        assertEquals(studyDbId, result.get(0).getStudyDbId());

        ArgumentCaptor<BrAPIStudySearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIStudySearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class), any(Function3.class), requestCaptor.capture());

        assertEquals(List.of("brapi-program-1"), requestCaptor.getValue().getProgramDbIds());
        assertEquals(List.of(studyDbId), requestCaptor.getValue().getStudyDbIds());

        verify(programCache, never()).get(any(UUID.class));
    }

    @Test
    @SneakyThrows
    void getStudyByDbIdReturnsEmptyWhenBrAPIDoesNotFindStudy() {
        when(brAPIDAOUtil.search(any(Function.class), any(Function3.class), any(BrAPIStudySearchRequest.class)))
                .thenReturn(List.of());

        Optional<BrAPIStudy> result = studyDAO.getStudyByDbId(UUID.randomUUID().toString(), program);

        assertTrue(result.isEmpty());
    }

    private BrAPIStudy study(UUID environmentId, String studyName) {
        BrAPIExternalReference studyReference = new BrAPIExternalReference()
                .referenceSource(Utilities.generateReferenceSource(REFERENCE_SOURCE, ExternalReferenceSource.STUDIES))
                .referenceId(environmentId.toString());

        return new BrAPIStudy()
                .studyName(studyName)
                .externalReferences(List.of(studyReference));
    }
}
