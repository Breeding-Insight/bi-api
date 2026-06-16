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

package org.breedinginsight.api.v1.controller.geno;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.GenotypeImportDetails;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenotypeService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GenotypeDataUploadControllerIntegrationTest extends DatabaseTest {

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @Inject
    private GenotypeService genotypeService;

    @Inject
    private DSLContext dsl;

    @Inject
    private ProgramDAO programDAO;

    @Inject
    private UserDAO userDAO;

    @Inject
    private ProgramService programService;

    private Program program;
    private User testUser;

    @MockBean(GenotypeService.class)
    GenotypeService genotypeService() {
        return mock(GenotypeService.class);
    }

    @MockBean(ProgramService.class)
    ProgramService programService() {
        return mock(ProgramService.class);
    }

    @BeforeAll
    void setup() {
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        dsl.execute(fp.get("InsertPrograms"));
        program = programDAO.getAll().get(0);
        testUser = userDAO.getUserByOAuthId(TestTokenValidator.TEST_USER_ORCID).orElseThrow();
        dsl.execute(fp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId());
    }

    @BeforeEach
    void resetMocks() {
        reset(genotypeService);
        reset(programService);
    }

    @Test
    void uploadDataUsesSubmissionScopedRouteAndServiceContract() throws Exception {
        UUID submissionId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();

        ImportResponse importResponse = new ImportResponse();
        importResponse.setImportId(importId);
        importResponse.setProgress(ImportProgress.builder()
                .statuscode((short) HttpStatus.ACCEPTED.getCode())
                .build());

        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());
        doReturn(importResponse).when(genotypeService)
                .submitGenotypeData(eq(testUser.getId()), eq(program.getId()), eq(submissionId), any(CompletedFileUpload.class));

        HttpResponse<String> response = client.exchange(
                POST(String.format("/programs/%s/submissions/%s/geno/import", program.getId(), submissionId), multipartBody())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());
        verify(genotypeService).submitGenotypeData(eq(testUser.getId()), eq(program.getId()), eq(submissionId), any(CompletedFileUpload.class));
    }

    @Test
    void experimentScopedUploadRouteIsRemoved() {
        UUID experimentId = UUID.randomUUID();

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                POST(String.format("/programs/%s/experiments/%s/geno/import", program.getId(), experimentId), multipartBody())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst());

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(genotypeService);
    }

    @Test
    void getGenotypeImportsReturnsPagedAndSortedResponse() throws DoesNotExistException {
        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());
        doReturn(Optional.of(program)).when(programService).getById(program.getId());

        GenotypeImportDetails older = GenotypeImportDetails.builder()
                .sampleSubmissionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .projectNameForSampleSubmission("Older Submission")
                .sampleSubmissionCreatedBy("Test User")
                .genotypingFileName("older.vcf")
                .genotypingImportDate(OffsetDateTime.parse("2026-06-01T10:00:00Z"))
                .genotypingImportBy("Importer A")
                .build();

        GenotypeImportDetails newer = GenotypeImportDetails.builder()
                .sampleSubmissionId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .projectNameForSampleSubmission("Newer Submission")
                .sampleSubmissionCreatedBy("Test User")
                .genotypingFileName("newer.vcf")
                .genotypingImportDate(OffsetDateTime.parse("2026-06-02T10:00:00Z"))
                .genotypingImportBy("Importer B")
                .build();

        doReturn(new ArrayList<>(Arrays.asList(older, newer))) // mutable list required because ResponseUtils sorts in place
                .when(genotypeService)
                .getGenotypeImports(program.getId());

        HttpResponse<String> response = client.exchange(
                GET(String.format("/programs/%s/geno/imports?page=1&pageSize=1&sortField=genotypingImportDate&sortOrder=DESC", program.getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject pagination = body.getAsJsonObject("metadata").getAsJsonObject("pagination");
        JsonArray data = body.getAsJsonObject("result").getAsJsonArray("data");

        assertEquals(2, pagination.get("totalCount").getAsInt());
        assertEquals(1, pagination.get("pageSize").getAsInt());
        assertEquals(2, pagination.get("totalPages").getAsInt());
        assertEquals(1, pagination.get("currentPage").getAsInt());
        assertEquals(1, data.size());

        JsonObject firstRow = data.get(0).getAsJsonObject();
        assertEquals("22222222-2222-2222-2222-222222222222", firstRow.get("sampleSubmissionId").getAsString());
        assertEquals("Newer Submission", firstRow.get("projectNameForSampleSubmission").getAsString());
        assertEquals("Test User", firstRow.get("sampleSubmissionCreatedBy").getAsString());
        assertEquals("newer.vcf", firstRow.get("genotypingFileName").getAsString());
        assertEquals("Importer B", firstRow.get("genotypingImportBy").getAsString());

        verify(programService).getBrapiEndpoints(program.getId());
        verify(programService).getById(program.getId());
        verify(genotypeService).getGenotypeImports(program.getId());
    }

    @Test
    void getGenotypeImportsReturnsNotFoundWhenProgramLookupFails() throws DoesNotExistException {
        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());
        doReturn(Optional.empty()).when(programService).getById(program.getId());

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                GET(String.format("/programs/%s/geno/imports?page=1&pageSize=10", program.getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst());

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());

        verify(programService).getBrapiEndpoints(program.getId());
        verify(programService).getById(program.getId());
        verifyNoInteractions(genotypeService);
    }

    @Test
    void getGenotypeImportsRejectsInvalidSortField() throws DoesNotExistException {
        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                GET(String.format("/programs/%s/geno/imports?page=1&pageSize=10&sortField=badField&sortOrder=DESC", program.getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst());

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());

        verify(programService).getBrapiEndpoints(program.getId());
        verifyNoInteractions(genotypeService);
    }

    @Test
    void getGenotypeImportsFiltersByProjectNameForSampleSubmission() throws DoesNotExistException {
        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());
        doReturn(Optional.of(program)).when(programService).getById(program.getId());

        GenotypeImportDetails older = GenotypeImportDetails.builder()
                .sampleSubmissionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .projectNameForSampleSubmission("Older Submission")
                .sampleSubmissionCreatedBy("Test User")
                .genotypingFileName("older.vcf")
                .genotypingImportDate(OffsetDateTime.parse("2026-06-01T10:00:00Z"))
                .genotypingImportBy("Importer A")
                .build();

        GenotypeImportDetails newer = GenotypeImportDetails.builder()
                .sampleSubmissionId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .projectNameForSampleSubmission("Newer Submission")
                .sampleSubmissionCreatedBy("Test User")
                .genotypingFileName("newer.vcf")
                .genotypingImportDate(OffsetDateTime.parse("2026-06-02T10:00:00Z"))
                .genotypingImportBy("Importer B")
                .build();

        doReturn(new ArrayList<>(Arrays.asList(older, newer))) //safe mutable list
                .when(genotypeService)
                .getGenotypeImports(program.getId());
        String param = URLEncoder.encode("Newer Submission", StandardCharsets.UTF_8);
        HttpResponse<String> response = client.exchange(
                GET(String.format(
                        "/programs/%s/geno/imports?page=1&pageSize=10&projectNameForSampleSubmission=" + param,
                        program.getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject pagination = body.getAsJsonObject("metadata").getAsJsonObject("pagination");
        JsonArray data = body.getAsJsonObject("result").getAsJsonArray("data");

        assertEquals(1, pagination.get("totalCount").getAsInt());
        assertEquals(10, pagination.get("pageSize").getAsInt());
        assertEquals(1, pagination.get("totalPages").getAsInt());
        assertEquals(1, pagination.get("currentPage").getAsInt());
        assertEquals(1, data.size());

        JsonObject firstRow = data.get(0).getAsJsonObject();
        assertEquals("22222222-2222-2222-2222-222222222222", firstRow.get("sampleSubmissionId").getAsString());
        assertEquals("Newer Submission", firstRow.get("projectNameForSampleSubmission").getAsString());
        assertEquals("newer.vcf", firstRow.get("genotypingFileName").getAsString());

        verify(programService).getBrapiEndpoints(program.getId());
        verify(programService).getById(program.getId());
        verify(genotypeService).getGenotypeImports(program.getId());
    }

    @Test
    void getGenotypeImportsReturnsEmptyDataWhenFiltersDoNotMatch() throws DoesNotExistException {
        doReturn(getBrAPIEndpoints()).when(programService).getBrapiEndpoints(program.getId());
        doReturn(Optional.of(program)).when(programService).getById(program.getId());

        GenotypeImportDetails row = GenotypeImportDetails.builder()
                .sampleSubmissionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .projectNameForSampleSubmission("Older Submission")
                .sampleSubmissionCreatedBy("Test User")
                .genotypingFileName("older.vcf")
                .genotypingImportDate(OffsetDateTime.parse("2026-06-01T10:00:00Z"))
                .genotypingImportBy("Importer A")
                .build();

        doReturn(new ArrayList<>(Arrays.asList(row))) //safe mutable list
                .when(genotypeService)
                .getGenotypeImports(program.getId());

        HttpResponse<String> response = client.exchange(
                GET(String.format(
                        "/programs/%s/geno/imports?page=1&pageSize=10&projectNameForSampleSubmission=DoesNotMatch",
                        program.getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                String.class
        ).blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject pagination = body.getAsJsonObject("metadata").getAsJsonObject("pagination");
        JsonArray data = body.getAsJsonObject("result").getAsJsonArray("data");

        assertEquals(0, pagination.get("totalCount").getAsInt());
        assertEquals(0, data.size());

        verify(programService, times(1)).getBrapiEndpoints(program.getId());
        verify(programService, times(1)).getById(program.getId());
        verify(genotypeService, times(1)).getGenotypeImports(program.getId());
    }

    private MultipartBody multipartBody() {
        return MultipartBody.builder()
                .addPart("file", new File("src/test/resources/files/geno/sample.vcf"))
                .build();
    }

    private ProgramBrAPIEndpoints getBrAPIEndpoints() {
        return ProgramBrAPIEndpoints.builder()
                .coreUrl(Optional.of("http://localhost:8081/"))
                .phenoUrl(Optional.of("http://localhost:8081/"))
                .genoUrl(Optional.of("http://localhost:8081/"))
                .build();
    }
}
