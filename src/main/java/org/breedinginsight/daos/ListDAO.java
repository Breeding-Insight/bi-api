package org.breedinginsight.daos;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.ListsApi;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.model.delta.DeltaEntityFactory;
import org.breedinginsight.model.delta.DeltaListDetails;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ListDAO {
    private final ProgramDAO programDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final DeltaEntityFactory deltaEntityFactory;
    private final ProgramService programService;

    @Inject
    public ListDAO(ProgramDAO programDAO,
                   BrAPIDAOUtil brAPIDAOUtil,
                   BrAPIEndpointProvider brAPIEndpointProvider,
                   DeltaEntityFactory deltaEntityFactory, ProgramService programService) {
        this.programDAO = programDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.deltaEntityFactory = deltaEntityFactory;
        this.programService = programService;
    }

    public DeltaListDetails getDeltaListDetailsByDbId(String listDbId, UUID programId) throws ApiException {
        ListsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ListsApi.class);
        ApiResponse<BrAPIListsSingleResponse> response = api.listsListDbIdGet(listDbId);
        if (Objects.isNull(response.getBody()) || Objects.isNull(response.getBody().getResult()))
        {
            throw new ApiException();
        }

        BrAPIListDetails details = response.getBody().getResult();
        return deltaEntityFactory.makeDeltaListDetailsBean(details);
    }

    public void deleteBrAPIList(String listDbId, UUID programId, boolean hardDelete) throws ApiException {
        var programBrAPIBaseUrl = getProgramBrAPIBaseUrl(programId);
        var requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/lists/" + listDbId).newBuilder();
        requestUrl.addQueryParameter("hardDelete", Boolean.toString(hardDelete));
        HttpUrl url = requestUrl.build();
        var brapiRequest = new Request.Builder().url(url)
                .method("DELETE", null)
                .addHeader("Content-Type", "application/json")
                .build();

        makeCall(brapiRequest);
    }

    private void makeCall(Request brapiRequest) throws ApiException {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        try {
            client.newCall(brapiRequest).execute();
        } catch (IOException e) {
            log.error("Error calling BrAPI Service", e);
            throw new ApiException("Error calling BrAPI Service");
        }
    }

    private String getProgramBrAPIBaseUrl(UUID programId) {
        ProgramBrAPIEndpoints programBrAPIEndpoints;
        try {
            programBrAPIEndpoints = programService.getBrapiEndpoints(programId);
        } catch (DoesNotExistException e) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program does not exist");
        }

        if(programBrAPIEndpoints.getCoreUrl().isEmpty()) {
            log.error("Program: " + programId + " is missing BrAPI URL config");
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "");
        }
        var programBrAPIBaseUrl = programBrAPIEndpoints.getCoreUrl().get();
        programBrAPIBaseUrl = programBrAPIBaseUrl.endsWith("/") ? programBrAPIBaseUrl.substring(0, programBrAPIBaseUrl.length() - 1) : programBrAPIBaseUrl;
        return programBrAPIBaseUrl.endsWith(BrapiVersion.BRAPI_V2) ? programBrAPIBaseUrl : programBrAPIBaseUrl + BrapiVersion.BRAPI_V2;
    }
}
