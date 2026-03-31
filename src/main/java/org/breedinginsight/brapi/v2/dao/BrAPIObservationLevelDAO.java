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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationLevelNamesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponseResult;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelSingleResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
public class BrAPIObservationLevelDAO {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final Gson gson = new JSON().getGson();

    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramDAO programDAO;

    @Inject
    public BrAPIObservationLevelDAO(BrAPIDAOUtil brAPIDAOUtil,
                                    BrAPIEndpointProvider brAPIEndpointProvider,
                                    ProgramDAO programDAO) {
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.programDAO = programDAO;
    }

    public HttpResponse<String> createObservationLevelName(Program program, String levelName, DatasetLevel levelOrder, String programDbId) {
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

    public BrAPIObservationUnitHierarchyLevel createLevelName(Program program,
                                                                          String programDbId,
                                                                          String levelName,
                                                                          DatasetLevel levelOrder) throws ApiException {
        ObservationLevelNamesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationLevelNamesApi.class);

        ApiResponse<BrAPIObservationLevelListResponse> response;


        BrAPIObservationUnitHierarchyLevel level = new BrAPIObservationUnitHierarchyLevel();

        level.setLevelName(levelName.toLowerCase());
        level.setLevelOrder(levelOrder.getValue());
        level.setProgramDbId(programDbId);

        try {
            response = api.observationLevelNamesPost(List.of(level));
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        return Optional.of(response)
                .map(ApiResponse::getBody)
                .map(BrAPIObservationLevelListResponse::getResult)
                .map(BrAPIObservationLevelListResponseResult::getData)
                .flatMap(data -> data.stream().findFirst())
                .orElseThrow(() -> new ApiException(String.format("BrAPI indicated level name [%s] was created but no levelNameDbId was returned upon its creation", levelName)));
    }

    public List<BrAPIObservationUnitHierarchyLevel> getObservationLevelNamesByProgramId(Program program, String programDbId) {
        ObservationLevelNamesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationLevelNamesApi.class);

        ApiResponse<BrAPIObservationLevelListResponse> response;

        int pageSize = 100;

        try {
            response = api.observationLevelNamesGet(programDbId,
                    false,
                    0,
                    pageSize);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        if (response.getBody().getMetadata().getPagination().getTotalCount() > 100) {
            throw new InternalServerException(String.format("More level names exist than requested [%s]", pageSize));
        }

        return response.getBody().getResult().getData();
    }

    public List<BrAPIObservationUnitHierarchyLevel> getGlobalObservationLevelNames(Program program) {
        return getObservationLevelNamesByProgramId(program, null);
    }

    public void deleteObservationLevelName(Program program, String levelDbId) {
        ObservationLevelNamesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationLevelNamesApi.class);

        try {
            api.observationLevelNameDbIdDelete(levelDbId);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }
    }

    public BrAPIObservationUnitHierarchyLevel updateObservationLevelName(Program program,
                                                                         String levelNameDbId,
                                                                         BrAPIObservationUnitHierarchyLevel level) {
        ObservationLevelNamesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationLevelNamesApi.class);

        ApiResponse<BrAPIObservationLevelSingleResponse> response;

        try {
            response = api.observationLevelNameDbIdPut(levelNameDbId, level);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        return response.getBody().getResult();
    }

}
