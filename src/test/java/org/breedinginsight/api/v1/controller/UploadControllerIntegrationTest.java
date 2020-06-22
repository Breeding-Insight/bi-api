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

import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;

import static io.micronaut.http.HttpRequest.PUT;
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
    void postProgramUploadsXlsSuccess() {

    }

    @Test
    void putTraitUploadCsvSuccess() {

        File file = new File("src/test/resources/files/data_one_row.csv");
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("file",
                        file
                ).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId()+"/trait-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

    }


}
