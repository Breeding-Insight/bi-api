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

package org.breedinginsight.utilities.response;

import com.google.gson.JsonArray;
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
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.dao.db.tables.daos.PlaceDao;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.PlaceEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResponseUtilsIntegrationTest extends DatabaseTest {

    ProgramEntity validProgram;
    List<PlaceEntity> locations;

    private FannyPack fp;
    @Inject
    private DSLContext dsl;
    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;
    @Inject
    private ProgramDao programDao;
    @Inject
    private PlaceDao locationDao;

    // Set up program locations
    @BeforeAll
    @SneakyThrows
    public void setup() {

        // Insert our traits into the db
        fp = FannyPack.fill("src/test/resources/sql/ResponseUtilsIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program locations
        dsl.execute(fp.get("InsertProgramLocations"));

        validProgram = programDao.findAll().get(0);

        locations = locationDao.findAll();
    }

    @Test
    public void getNoQuery() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(locations.size(), data.size(), "Wrong number of results returned");
    }

    // Negative page number
    @Test
    public void getNegativePageNumber() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?page=-2").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // Negative page size
    @Test
    public void getNegativePageSize() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?pageSize=-50").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // Higher page than is available
    @Test
    public void getHigherPageThanAvailable() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?page=1000").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(0, data.size(), "Wrong number of results returned");
    }

    // Sort field does not exist
    @Test
    public void getSortFieldNoExist() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=no_exist").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // Bad sort order
    @Test
    public void getBadSortOrder() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortOrder=UP_DOWN").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // Bad search field
    @Test
    public void postBadSearchField() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());
        searchRequest.getFilter().add(FilterRequest.builder().field("no_exist").value("no_exist").build());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortOrder=UP_DOWN", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // No search value
    @Test
    public void postNoSearchValue() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());
        searchRequest.getFilter().add(FilterRequest.builder().field("no_exist").value(null).build());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortOrder=UP_DOWN", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // POST no body
    @Test
    public void postNoBody() {
        SearchRequest searchRequest = new SearchRequest();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortOrder=UP_DOWN", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Wrong status returned");
    }

    // POST no filter fields
    @Test
    public void postNoFilterFieldsSuccess() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(locations.size(), data.size(), "Wrong number of results returned");
    }

    // GET Sort ascending success
    @Test
    public void getAscendingSortSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=name&sortOrder=ASC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        TestUtils.checkStringSorting(data, "name", SortOrder.ASC);
    }

    // Get sort descending success
    @Test
    public void getDescendingSortSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=name&sortOrder=DESC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        TestUtils.checkStringSorting(data, "name", SortOrder.DESC);
    }


    // Get integer sort success
    @Test
    public void getNumericSortDescendingSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=slope&sortOrder=DESC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        TestUtils.checkNumericSorting(data, "slope", SortOrder.DESC);
    }

    // Get pagination column with nulls success
    @Test
    public void getSortAscendingWithNullsSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=abbreviation&sortOrder=ASC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        // Nulls should be at top, they are considered lowest value
        assertEquals(false, data.get(0).getAsJsonObject().has("abbreviation"), "abbreviation should not be present");
        assertEquals(true, data.get(data.size() - 1).getAsJsonObject().has("abbreviation"), "abbreviation should be present");

    }

    // Get date sort success
    @Test
    public void getSortAscendingWithDatesSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/locations?sortField=createdAt&sortOrder=ASC").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        TestUtils.checkDateSorting(data, "createdAt", SortOrder.ASC);
    }

    // POST Sort success
    @Test
    public void postDescendingSortSuccess() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortField=name&sortOrder=DESC", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        TestUtils.checkStringSorting(data, "name", SortOrder.DESC);
    }

    // POST Single search success
    @Test
    public void postSingleSearchSuccess() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());
        searchRequest.getFilter().add(FilterRequest.builder().field("name").value("place1").build());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortField=name&sortOrder=DESC", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        // Should be 11 results, place1, place10 -> place19
        assertEquals(11, data.size(), "Wrong number of results returned");
        TestUtils.checkStringSorting(data, "name", SortOrder.DESC);
    }

    // POST Multiple search success
    @Test
    public void postMultipleSearchSuccess() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());
        searchRequest.getFilter().add(FilterRequest.builder().field("name").value("place1").build());
        searchRequest.getFilter().add(FilterRequest.builder().field("slope").value("1.1").build());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        // Should be 1 result
        assertEquals(1, data.size(), "Wrong number of results returned");
    }

    // POST Search plus pagination
    @Test
    public void postSearchWithPaginationSuccess() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilter(new ArrayList<>());
        searchRequest.getFilter().add(FilterRequest.builder().field("name").value("place1").build());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/locations/search?sortField=name&sortOrder=DESC&pageSize=5&page=1", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        // Should be 5 out of 11 results, place1, place10 -> place19
        assertEquals(5, data.size(), "Wrong number of results returned");
        TestUtils.checkStringSorting(data, "name", SortOrder.DESC);

        JsonObject metadata = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("metadata");
        JsonObject pagination = metadata.getAsJsonObject("pagination");
        assertEquals(3, pagination.get("totalPages").getAsInt(), "Wrong total pages");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
        assertEquals(5, pagination.get("pageSize").getAsInt(), "Wrong page size");
        assertEquals(11, pagination.get("totalCount").getAsInt(), "Wrong total count");
    }
}
