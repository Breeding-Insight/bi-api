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

import com.google.gson.JsonObject;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Trait;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UploadControllerIntegrationTest {

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private ProgramEntity validProgram;
    private File validFile = new File("src/test/resources/files/data_one_row.csv");
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    String invalidProgram = invalidUUID;

    @BeforeAll
    public void setup() {

        // Insert test data into the db
        var fp = FannyPack.fill("src/test/resources/sql/UploadControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert user into program
        dsl.execute(fp.get("InsertProgramUser"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);

    }

    @Test
    void putTraitUploadInvalidProgramId() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(invalidProgram, validFile, "test-registered-user");
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putTraitUploadUserNotInProgram() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), validFile, "other-registered-user");
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putTraitUploadMissingMediaType() {
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("file","test", new byte[1]).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId()+"/trait-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadUnsupportedMimeType() {
        File file = new File("src/test/resources/files/unsupported.txt");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadMissingRequiredColumn() {
        File file = new File("src/test/resources/files/missing_method_name_with_data.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void putTraitUploadMissingFormula() {
        File file = new File("src/test/resources/files/missing_formula.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void putTraitUploadMissingCategories() {
        File file = new File("src/test/resources/files/missing_categories.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(1)
    void getTraitUploadDoesNotExist() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId()+"/trait-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void deleteTraitUploadInvalidProgram() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+invalidProgram+"/trait-upload")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putTraitUploadXlsxSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xlsx");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    void putTraitUploadXlsSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xls");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @Order(2)
    void putTraitUploadCsvSuccess() {

        File file = new File("src/test/resources/files/data_one_row.csv");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());

    }

    private HttpResponse<String> uploadFile(String programId, File file, String user) {
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("file",
                        file
                ).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+programId+"/trait-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", user)), String.class
        );

        return call.blockingFirst();
    }

    private void checkTraitEqual(Trait expected, Trait actual) {

    }

    @Test
    @Order(3)
    void getTraitUploadSuccess() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId()+"/trait-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @Order(4)
    void deleteTraitUploadSuccess() {

    }

}
