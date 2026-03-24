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

package org.breedinginsight.brapi.v2.dao;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class BrAPIObservationLevelDAO {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final Gson gson = new JSON().getGson();

    @Inject
    public BrAPIObservationLevelDAO(BrAPIDAOUtil brAPIDAOUtil) {
        this.brAPIDAOUtil = brAPIDAOUtil;
    }

    public HttpResponse<String> createObservationLevelName(Program program, String levelName, DatasetLevel levelOrder, String programDbId) throws ApiException {
        HttpUrl url = HttpUrl.parse(brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId()))
                             .newBuilder()
                             .addPathSegment("observationlevelnames")
                             .build();
        JsonObject levelJson = new JsonObject();
        levelJson.addProperty("levelName", levelName);
        if (levelOrder != null) {
            levelJson.addProperty("levelOrder", levelOrder.getValue());
        }
        if (programDbId != null) {
            levelJson.addProperty("programDbId", programDbId);
        }
        JsonArray bodyArray = new JsonArray();
        bodyArray.add(levelJson);
        RequestBody body = RequestBody.create(gson.toJson(bodyArray), JSON_MEDIA_TYPE);
        var request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        return brAPIDAOUtil.makeCall(request);
    }

    public void deleteObservationLevelName(Program program, String levelDbId) {
        HttpUrl url = HttpUrl.parse(brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId()))
                             .newBuilder()
                             .addPathSegment("observationlevelnames")
                             .addPathSegment(levelDbId)
                             .build();
        var request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            HttpResponse<String> response = brAPIDAOUtil.makeCall(request);
            if (response.getStatus() != HttpStatus.OK && response.getStatus() != HttpStatus.NO_CONTENT && response.getStatus() != HttpStatus.ACCEPTED) {
                log.warn("Observation level delete returned status {} for {}", response.getStatus(), levelDbId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete observation level {}", levelDbId, e);
        }
    }

    public List<String> getObservationLevelNames(Program program, String programDbId) throws ApiException {
        List<String> levelNames = new ArrayList<>();
        int currentPage = 0;
        int totalPages = 1;

        do {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId()))
                    .newBuilder()
                    .addPathSegment("observationlevelnames")
                    .addQueryParameter("page", Integer.toString(currentPage))
                    .addQueryParameter("pageSize", "1000");
            if (StringUtils.isNotBlank(programDbId)) {
                urlBuilder.addQueryParameter("programDbId", programDbId);
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .get()
                    .addHeader("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = brAPIDAOUtil.makeCall(request);
            if (response.getStatus() != HttpStatus.OK) {
                throw new ApiException(response.getStatus().getCode(), "Unable to fetch observation level names");
            }

            String responseBody = response.body();
            if (StringUtils.isBlank(responseBody)) {
                return levelNames;
            }

            JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject resultJson = responseJson.getAsJsonObject("result");
            if (resultJson != null) {
                JsonArray data = resultJson.getAsJsonArray("data");
                if (data != null) {
                    for (JsonElement level : data) {
                        if (level.isJsonObject()) {
                            JsonElement levelName = level.getAsJsonObject().get("levelName");
                            if (levelName != null && !levelName.isJsonNull()) {
                                levelNames.add(levelName.getAsString());
                            }
                        }
                    }
                }
            }

            JsonObject metadata = responseJson.getAsJsonObject("metadata");
            JsonObject pagination = metadata != null ? metadata.getAsJsonObject("pagination") : null;
            totalPages = pagination != null && pagination.has("totalPages") ? pagination.get("totalPages").getAsInt() : currentPage + 1;
            currentPage++;
        } while (currentPage < totalPages);

        return levelNames;
    }

}
