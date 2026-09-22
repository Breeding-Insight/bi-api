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

package org.breedinginsight.brapi.v2;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIPagination;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponseResult;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.brapi.v2.model.request.query.StudyQuery;
import org.breedinginsight.brapi.v2.services.BrAPIStudyService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.services.ExperimentalCollaboratorService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.utilities.response.mappers.StudyQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class BrAPIStudiesControllerUnitTest {

    private BrAPIStudyService studyService;
    private ProgramService programService;
    private SecurityService securityService;
    private ProgramUserService programUserService;
    private ExperimentalCollaboratorService experimentalCollaboratorService;
    private BrAPIStudiesController controller;
    private Program program;
    private UUID programId;
    private UUID userId;

    @BeforeEach
    void setup() {
        studyService = mock(BrAPIStudyService.class);
        programService = mock(ProgramService.class);
        securityService = mock(SecurityService.class);
        programUserService = mock(ProgramUserService.class);
        experimentalCollaboratorService = mock(ExperimentalCollaboratorService.class);

        controller = new BrAPIStudiesController(
                studyService,
                new StudyQueryMapper(),
                programService,
                securityService,
                programUserService,
                experimentalCollaboratorService);

        programId = UUID.randomUUID();
        userId = UUID.randomUUID();
        program = new Program();
        program.setId(programId);

        AuthenticatedUser user = new AuthenticatedUser("test-user", List.of(), userId, List.of());
        when(programService.getById(programId)).thenReturn(Optional.of(program));
        when(securityService.getUser()).thenReturn(user);
        when(programUserService.getIfExperimentalCollaborator(programId, userId)).thenReturn(Optional.empty());
    }

    @Test
    @SneakyThrows
    void getStudiesDelegatesQueryToProdServerAndUsesReturnedPagination() {
        StudyQuery query = new StudyQuery();
        query.setSortField("studyCode");
        query.setSortOrder(SortOrder.DESC);
        query.setPage(1);
        query.setPageSize(2);

        List<BrAPIStudy> studies = List.of(
                new BrAPIStudy().studyName("Environment 2"),
                new BrAPIStudy().studyName("Environment 1"));
        BrAPIStudyListResponse brAPIResponse = studyResponse(studies, 1, 2, 5);
        when(studyService.searchStudies(same(program), same(query)))
                .thenReturn(brAPIResponse);

        HttpResponse<Response<DataResponse<BrAPIStudy>>> response = controller.getStudies(programId, query);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(studies, response.body().getResult().getData());
        assertEquals(1, response.body().getMetadata().getPagination().getCurrentPage());
        assertEquals(2, response.body().getMetadata().getPagination().getPageSize());
        assertEquals(5, response.body().getMetadata().getPagination().getTotalCount());
        assertEquals(3, response.body().getMetadata().getPagination().getTotalPages());
        assertEquals("studyCode", query.getSortField());
        assertEquals(SortOrder.DESC, query.getSortOrder());

        verify(studyService).searchStudies(same(program), same(query));
        verify(studyService, never()).getStudies(any(UUID.class));
    }

    @Test
    @SneakyThrows
    void getStudiesAppliesDefaultSortOnlyWhenSortIsMissing() {
        StudyQuery query = new StudyQuery();
        query.setSortField(null);
        query.setSortOrder(null);
        BrAPIStudyListResponse brAPIResponse = studyResponse(List.of(), 0, 50, 0);
        when(studyService.searchStudies(same(program), same(query)))
                .thenReturn(brAPIResponse);

        controller.getStudies(programId, query);

        assertEquals("studyName", query.getSortField());
        assertEquals(SortOrder.ASC, query.getSortOrder());
        verify(studyService).searchStudies(same(program), same(query));
    }

    @Test
    @SneakyThrows
    void getStudiesLimitsCollaboratorSearchToAuthorizedExperiments() {
        StudyQuery query = new StudyQuery();
        ProgramUser collaborator = new ProgramUser();
        UUID programUserRoleId = UUID.randomUUID();
        collaborator.setId(programUserRoleId);
        List<UUID> authorizedExperimentIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<BrAPIStudy> studies = List.of(new BrAPIStudy().studyName("Authorized Environment"));

        when(programUserService.getIfExperimentalCollaborator(programId, userId))
                .thenReturn(Optional.of(collaborator));
        when(experimentalCollaboratorService.getAuthorizedExperimentIds(programUserRoleId))
                .thenReturn(authorizedExperimentIds);
        BrAPIStudyListResponse brAPIResponse = studyResponse(studies, 0, 50, 1);
        when(studyService.searchStudies(same(program), same(authorizedExperimentIds), same(query)))
                .thenReturn(brAPIResponse);

        HttpResponse<Response<DataResponse<BrAPIStudy>>> response = controller.getStudies(programId, query);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(studies, response.body().getResult().getData());
        verify(studyService).searchStudies(same(program), same(authorizedExperimentIds), same(query));
        verify(studyService, never()).searchStudies(any(Program.class), any(StudyQuery.class));
    }

    @Test
    void getStudiesReturnsEmptyResponseWithoutCallingProdServerWhenCollaboratorHasNoAuthorizedExperiments() {
        StudyQuery query = new StudyQuery();
        ProgramUser collaborator = new ProgramUser();
        UUID programUserRoleId = UUID.randomUUID();
        collaborator.setId(programUserRoleId);

        when(programUserService.getIfExperimentalCollaborator(programId, userId))
                .thenReturn(Optional.of(collaborator));
        when(experimentalCollaboratorService.getAuthorizedExperimentIds(programUserRoleId))
                .thenReturn(List.of());

        HttpResponse<Response<DataResponse<BrAPIStudy>>> response = controller.getStudies(programId, query);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.body().getResult().getData().isEmpty());
        assertEquals(0, response.body().getMetadata().getPagination().getTotalCount());
        assertEquals(0, response.body().getMetadata().getPagination().getTotalPages());
        verifyNoInteractions(studyService);
    }

    private BrAPIStudyListResponse studyResponse(List<BrAPIStudy> studies,
                                                 int currentPage,
                                                 int pageSize,
                                                 int totalCount) {
        BrAPIPagination pagination = mock(BrAPIPagination.class);
        when(pagination.getCurrentPage()).thenReturn(currentPage);
        when(pagination.getPageSize()).thenReturn(pageSize);
        when(pagination.getTotalCount()).thenReturn(totalCount);

        return new BrAPIStudyListResponse()
                .metadata(new BrAPIMetadata().pagination(pagination))
                .result(new BrAPIStudyListResponseResult().data(studies));
    }
}
