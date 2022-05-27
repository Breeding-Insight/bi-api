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

package org.breedinginsight.api.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;

import java.util.Optional;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JobControllerIntegrationTest extends DatabaseTest {

    private User testUser;
    private Program program;

    @MockBean(ProgramService.class)
    ProgramService programService() {
        return mock(ProgramService.class);
    }

    @Inject
    private ProgramService programService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @Inject
    private DSLContext dsl;

    @Inject
    private ProgramDAO programDAO;

    @Inject
    private UserDAO userDAO;

    @BeforeAll
    public void setup() {
        try {
            var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
            dsl.execute(securityFp.get("InsertPrograms"));


            var programs = programDAO.getAll();
            program = programs.get(0);

            // Insert system roles
            testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID)
                              .get();
            dsl.execute(securityFp.get("InsertSystemRoleAdmin"),
                        testUser.getId()
                                .toString());
            dsl.execute(securityFp.get("InsertProgramRolesBreeder"),
                        testUser.getId()
                                .toString(),
                        program.getId()
                               .toString());

            var jobFp = FannyPack.fill("src/test/resources/sql/JobControllerIntegrationTest.sql");
            dsl.execute(jobFp.get("InsertJobs"), program.getId().toString(), program.getId().toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @AfterAll
    public void finish() { super.stopContainers(); }

    @Test
    public void fetchJobs() throws DoesNotExistException {
        try {
            when(programService.getById(program.getId())).thenReturn(Optional.of(program));
            when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(new ProgramBrAPIEndpoints());

            Flowable<HttpResponse<String>> call = client.exchange(
                    GET(String.format("/programs/%s/jobs", program.getId().toString()))
                            .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
            );

            HttpResponse<String> response = call.blockingFirst();
            assertEquals(HttpStatus.OK, response.getStatus());

            JsonObject result = JsonParser.parseString(response.body())
                                          .getAsJsonObject()
                                          .getAsJsonObject("result");
            JsonArray data = result.getAsJsonArray("data");
            for (JsonElement obj : data) {
                JsonObject job = (JsonObject) obj;
                assertNotNull(job.get("statuscode"));
                assertNotNull(job.get("statusMessage"));
                assertNotNull(job.get("jobType"));
                assertNotNull(job.get("createdAt"));
                assertNotNull(job.get("createdByUser"));
                assertNotNull(job.get("jobDetail"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

}
