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

import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.geno.GenotypeService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.io.File;
import java.util.UUID;

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

    private Program program;
    private User testUser;

    @MockBean(GenotypeService.class)
    GenotypeService genotypeService() {
        return mock(GenotypeService.class);
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

    private MultipartBody multipartBody() {
        return MultipartBody.builder()
                            .addPart("file", new File("src/test/resources/files/geno/sample.vcf"))
                            .build();
    }
}
