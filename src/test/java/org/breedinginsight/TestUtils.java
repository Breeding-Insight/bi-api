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

package org.breedinginsight;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.reactivex.Flowable;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.model.Program;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    public static void checkStringSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 1; i++){
            String firstValue = data.get(i).getAsJsonObject().get(field).getAsString();
            String secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsString();

            if (firstValue.compareTo(secondValue) == 0){
                continue;
            }
            
            if (sortOrder == SortOrder.ASC) {
                assertEquals(true, firstValue.compareTo(secondValue) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, firstValue.compareTo(secondValue) > 0, "Incorrect sorting");
            }

        }

    }

    public static void checkNumericSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 1; i++){
            if (!data.get(i).getAsJsonObject().has(field) || !data.get(i + 1).getAsJsonObject().has(field)) {
                continue;
            }
            Float firstValue = data.get(i).getAsJsonObject().get(field).getAsFloat();
            Float secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsFloat();

            if (firstValue.compareTo(secondValue) == 0){
                continue;
            }

            if (sortOrder == SortOrder.ASC) {
                assertEquals(true, firstValue.compareTo(secondValue) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, firstValue.compareTo(secondValue) > 0, "Incorrect sorting");
            }
        }

    }

    public static void checkDateSorting(JsonArray data, String field, SortOrder sortOrder) {

        for (int i = 0; i < data.size() - 1; i++){
            String firstValue = data.get(i).getAsJsonObject().get(field).getAsString();
            String secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsString();
            OffsetDateTime firstDate = OffsetDateTime.parse(firstValue);
            OffsetDateTime secondDate = OffsetDateTime.parse(secondValue);

            if (firstDate.compareTo(secondDate) == 0){
                continue;
            }

            if (sortOrder == SortOrder.ASC){
                assertEquals(true, firstDate.compareTo(secondDate) < 0,
                        String.format("Incorrect sorting. Sort order: %s, First Value: %s, Second Value: %s", sortOrder, firstDate, secondDate));
            } else {
                assertEquals(true, firstDate.compareTo(secondDate) > 0,
                        String.format("Incorrect sorting. Sort order: %s, First Value: %s, Second Value: %s", sortOrder, firstDate, secondDate));
            }
        }

    }

    public static void checkStringListSorting(List<List<String>> data, SortOrder sortOrder) {
        for (int i = 0; i < data.size() - 1; i++){

            if (data.get(i).size() == data.get(i + 1).size()){

                int result = String.join("", data.get(i)).compareToIgnoreCase(String.join("", data.get(i + 1)));
                if (result == 0){
                    continue;
                }

                if (sortOrder == SortOrder.ASC) {
                    assertEquals(true, result < 0, "Incorrect sorting");
                } else {
                    assertEquals(true, result > 0, "Incorrect sorting");
                }
            } else if (sortOrder == SortOrder.ASC){
                assertEquals(true, data.get(i).size() < data.get(i+1).size(), "Incorrect sorting");
            } else {
                assertEquals(true, data.get(i).size() > data.get(i+1).size(), "Incorrect sorting");
            }


        }
    }

    public static Program insertAndFetchTestProgram(Gson gson, RxHttpClient client, ProgramRequest programRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String programId = result.get("id").getAsString();

        Program program = getProgramById(gson, client, UUID.fromString(programId));

        return program;
    }

    public static Program getProgramById(Gson gson, RxHttpClient client, UUID programId) {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+programId.toString()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("result");

        Program program = gson.fromJson(result, Program.class);

        return program;
    }
}
