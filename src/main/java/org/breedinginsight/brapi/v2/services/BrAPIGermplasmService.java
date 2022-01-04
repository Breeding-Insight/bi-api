package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class BrAPIGermplasmService {

    private BrAPIProvider brAPIProvider;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;
    private final String BREEDING_METHOD_ID_KEY = "breedingMethodId";
    private final String GERMPLASM_NAME_REGEX = "^(.*\\b) \\[([A-Z]{2,6})-(\\d+)\\]$";

    @Inject
    public BrAPIGermplasmService(BrAPIProvider brAPIProvider) {
        this.brAPIProvider = brAPIProvider;
    }

    public List<BrAPIGermplasm> getGermplasm(UUID programId) {
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);

        // Set query params and make call
        GermplasmQueryParams query = new GermplasmQueryParams();
        query.externalReferenceSource(String.format("%s/programs", referenceSource));
        query.externalReferenceID(programId.toString());
        BrAPIGermplasmListResponse germplasmResponse;
        try {
            germplasmResponse = api.germplasmGet(query).getBody();
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage());
        }

        // Process the germplasm
        List<BrAPIGermplasm> germplasmList = new ArrayList<>();
        if (germplasmResponse.getResult() != null && germplasmResponse.getResult().getData() != null) {
            germplasmList = germplasmResponse.getResult().getData();
        }

        Map<String, BrAPIGermplasm> germplasmByFullName = new HashMap<>();
        for (BrAPIGermplasm germplasm: germplasmList) {
            germplasmByFullName.put(germplasm.getGermplasmName(), germplasm);

            JsonObject additionalInfo = germplasm.getAdditionalInfo();
            if (additionalInfo != null && additionalInfo.has(BREEDING_METHOD_ID_KEY)) {
                germplasm.setBreedingMethodDbId(additionalInfo.get(BREEDING_METHOD_ID_KEY).getAsString());
            }

            if (germplasm.getDefaultDisplayName() != null) {
                germplasm.setGermplasmName(germplasm.getDefaultDisplayName());
            }
        }

        // Update pedigree string
        for (BrAPIGermplasm germplasm: germplasmList) {
            if (germplasm.getPedigree() != null) {
                String newPedigreeString = germplasm.getPedigree();
                List<String> parents = Arrays.asList(germplasm.getPedigree().split("/"));
                if (parents.size() >= 1 && germplasmByFullName.containsKey(parents.get(0))) {
                    newPedigreeString = germplasmByFullName.get(parents.get(0)).getAccessionNumber();
                }
                if (parents.size() == 2 && germplasmByFullName.containsKey(parents.get(1))) {
                    newPedigreeString += "/" + germplasmByFullName.get(parents.get(1)).getAccessionNumber();
                }
                germplasm.setPedigree(newPedigreeString);
            }
        }

        return germplasmList;
    }
}
