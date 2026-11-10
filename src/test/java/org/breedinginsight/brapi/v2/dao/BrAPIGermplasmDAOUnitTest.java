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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import lombok.SneakyThrows;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
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

public class BrAPIGermplasmDAOUnitTest {

    private static final String BRAPI_PROGRAM_DB_ID = "brapi-program-1";

    private BrAPIGermplasmDAO germplasmDAO;
    private BrAPIDAOUtil brAPIDAOUtil;
    private Program program;

    @BeforeEach
    @SneakyThrows
    void setup() {
        UUID programId = UUID.randomUUID();

        program = new Program();
        program.setId(programId);
        program.setKey("TEST");

        ProgramDAO programDAO = mock(ProgramDAO.class);
        brAPIDAOUtil = mock(BrAPIDAOUtil.class);

        when(programDAO.fetchOneById(programId)).thenReturn(program);
        when(programDAO.getCoreClient(programId)).thenReturn(mock(BrAPIClient.class));
        when(brAPIDAOUtil.getBrAPIProgramDbId(programId)).thenReturn(BRAPI_PROGRAM_DB_ID);

        germplasmDAO = new BrAPIGermplasmDAO(programDAO, mock(ImportDAO.class),
                brAPIDAOUtil,
                new BrAPIEndpointProvider(),
                65000,
                new ObjectMapper(),
                mock(BrAPIListDAO.class)
        );
    }

    @Test
    @SneakyThrows
    void getGermplasmByRawNameUsesBrAPISearch() {
        List<String> germplasmNames = List.of("Germplasm A [TEST-1]", "Germplasm B [TEST-2]");

        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmName("Germplasm A [TEST-1]");
        germplasm.setDefaultDisplayName("Germplasm A");
        germplasm.setAccessionNumber("1");
        when(brAPIDAOUtil.search(any(Function.class), any(Function3.class), any(BrAPIGermplasmSearchRequest.class))).thenReturn(List.of(germplasm));

        List<BrAPIGermplasm> result = germplasmDAO.getGermplasmByRawName(germplasmNames, program.getId());

        ArgumentCaptor<BrAPIGermplasmSearchRequest> requestCaptor = ArgumentCaptor.forClass(BrAPIGermplasmSearchRequest.class);

        verify(brAPIDAOUtil).search(any(Function.class), any(Function3.class), requestCaptor.capture());
        verify(brAPIDAOUtil, never()).get(any(Function.class), any(GermplasmQueryParams.class));

        BrAPIGermplasmSearchRequest request = requestCaptor.getValue();

        assertEquals(List.of(BRAPI_PROGRAM_DB_ID), request.getProgramDbIds());
        assertEquals(germplasmNames, request.getGermplasmNames());

        assertEquals(1, result.size());
        assertEquals("Germplasm A", result.get(0).getGermplasmName());
    }

    @Test
    @SneakyThrows
    void getGermplasmByRawNameWithEmptyNamesDoesNotSearch() {
        assertTrue(germplasmDAO.getGermplasmByRawName(List.of(), program.getId()).isEmpty());

        verify(brAPIDAOUtil, never()).search(any(Function.class), any(Function3.class), any(BrAPIGermplasmSearchRequest.class));
    }
}
