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

import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.util.List;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatasetsIntegrationTest extends BrAPITest {

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private String nonExistingId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    private DSLContext dsl;

    @Inject
    private ProgramDAO programDAO;
    @Inject
    private UserDAO userDAO;

    private User testUser;
    private Program program;


    @BeforeAll
    public void setup() {
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        dsl.execute(securityFp.get("InsertPrograms"));

        List<Program> programs = programDAO.getAll();
        program = programs.get(0);

        // Insert system roles
        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId().toString());
    }

    @Test
    public void getDatasetsInvalidProgram() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/datasets", nonExistingId, nonExistingId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void getDatasetsInvalidExperiment() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/datasets", program.getId().toString(), nonExistingId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }



}
