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

import com.google.gson.*;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.reactivex.Flowable;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import se.sawano.java.text.AlphanumericComparator;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtils {

    public static void checkStringSorting(JsonArray data, String field, SortOrder sortOrder) {

        Integer missedFields = 0;
        for (int i = 0; i < data.size() - 1; i++){
            if (!data.get(i).getAsJsonObject().has(field) || !data.get(i + 1).getAsJsonObject().has(field)) {
                missedFields += 1;
                continue;
            }
            String firstValue = data.get(i).getAsJsonObject().get(field).getAsString();
            String secondValue = data.get(i + 1).getAsJsonObject().get(field).getAsString();

            AlphanumericComparator comparator = new AlphanumericComparator(Locale.ENGLISH);
            if (comparator.compare(firstValue, secondValue) == 0) {
                continue;
            }
            
            if (sortOrder == SortOrder.ASC) {
                assertEquals(true, comparator.compare(firstValue, secondValue) < 0, "Incorrect sorting");
            } else {
                assertEquals(true, comparator.compare(firstValue, secondValue) > 0, "Incorrect sorting");
            }

        }

        assertTrue(missedFields < data.size() -1 || data.size() == 1, "No fields by the name " + field + "were found.");
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

                AlphanumericComparator comparator = new AlphanumericComparator();
                int result = comparator.compare(String.join("", data.get(i)).toLowerCase(), String.join("", data.get(i + 1)).toLowerCase());
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
                POST("/programs", gson.toJson(programRequest))
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

    public static HttpResponse<String> uploadDataFile(RxHttpClient client, UUID programID, String mappingID,
                                                         Map<String, String> body, File file) throws InterruptedException {
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", programID, mappingID);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String importId = result.get("importId").getAsString();

        // Process data
        String url = String.format("/programs/%s/import/mappings/%s/data/%s/commit", programID, mappingID, importId);
        Flowable<HttpResponse<String>> processCall = client.exchange(
                PUT(url, body)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        processCall.blockingFirst();

       return getUploadedFile(client, programID, mappingID, importId);
    }

    public static HttpResponse<String> getUploadedFile(RxHttpClient client, UUID programID, String mappingID, String importId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/import/mappings/%s/data/%s?mapping=true", programID, mappingID, importId))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        if (response.getStatus().equals(HttpStatus.ACCEPTED)) {
            Thread.sleep(1000);
            return getUploadedFile(client, programID, mappingID, importId);
        } else {
            return response;
        }
    }

    public static void insertTestTraits(Gson gson, RxHttpClient client, Program program, List<Trait> traits) {

        String url = String.format("/programs/%s/traits", program.getId());
        String json = gson.toJson(traits);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
    }

    public static void unzipFile(InputStream stream, String destDirPath) throws IOException {
        File destDir = new File(destDirPath);
        destDir.mkdirs();

        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(stream);
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = new File(destDir, zipEntry.getName());
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
    }
}
