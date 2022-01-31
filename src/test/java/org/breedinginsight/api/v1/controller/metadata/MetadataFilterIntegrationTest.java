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

package org.breedinginsight.api.v1.controller.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.api.v1.controller.UserController;
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataFilterIntegrationTest extends DatabaseTest {

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @Inject
    @Client("/")
    private RxHttpClient plainClient;

    @Inject
    private DSLContext dsl;
    @Inject
    private BiUserDao biUserDao;

    BiUserEntity testUser;
    BiUserEntity otherTestUser;

    private FannyPack fp = FannyPack.fill("src/test/resources/sql/UserControllerIntegrationTest.sql");

    @BeforeAll
    public void setup() {
        // Insert our traits into the db
        dsl.execute(fp.get("InsertProgram"));
        dsl.execute(fp.get("InsertUserProgramAssociations"));
        dsl.execute(fp.get("InsertManyUsers"));

        testUser = biUserDao.fetchByOrcid(TestTokenValidator.TEST_USER_ORCID).get(0);
        otherTestUser = biUserDao.fetchByOrcid(TestTokenValidator.OTHER_TEST_USER_ORCID).get(0);

        dsl.execute(fp.get("InsertSystemRoleAdmin"), testUser.getId().toString());
        dsl.execute(fp.get("InsertSystemRoleAdmin"), otherTestUser.getId().toString());
    }

    @AfterAll
    public void finish() {
        super.stopContainers();
    }

    @Test
    public void getSingleResponseNoMetadataSuccess() {

        // Check metadata is successfully populated when none is returned from controller

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users/" + testUser.getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");

        // Check our default page numbers are still set correctly
        assertEquals(1, data.getAsJsonPrimitive("totalPages").getAsInt(), "Default total pages is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("totalCount").getAsInt(), "Default total count is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("pageSize").getAsInt(), "Default page size is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("currentPage").getAsInt(), "Default current page is incorrect");
    }

    @Test
    public void getDataResponseMetadataFilterSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/users").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Test that our metadata object has been returned
        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");

        assertTrue(data != null, "Metadata was not populated");

        // Check our page numbers weren't altered by the filtered
        assertEquals(1, data.getAsJsonPrimitive("totalPages").getAsInt(), "Default total pages is incorrect");
        assertEquals(38, data.getAsJsonPrimitive("totalCount").getAsInt(), "Default total count is incorrect");
        assertEquals(50, data.getAsJsonPrimitive("pageSize").getAsInt(), "Default page size is incorrect");
        assertEquals(1, data.getAsJsonPrimitive("currentPage").getAsInt(), "Default current page is incorrect");
    }

    @Test
    public void filterNotCalledNoAnnotation(){

        Flowable<HttpResponse<String>> call = plainClient.exchange(
                GET("/health").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject metadata = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");

        assertTrue(metadata == null, "Metadata was populated, but shouldn't have been");

    }
}
