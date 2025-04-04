package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIListService {
    private final String referenceSource;
    private final BrAPIListDAO listDAO;
    private final BrAPIGermplasmDAO germplasmDAO;

    @Inject
    public BrAPIListService(@Property(name = "brapi.server.reference-source") String referenceSource,
                            BrAPIListDAO listDAO,
                            BrAPIGermplasmDAO germplasmDAO) {

        this.referenceSource = referenceSource;
        this.listDAO = listDAO;
        this.germplasmDAO = germplasmDAO;
    }

    public List<BrAPIListSummary> getListSummariesByTypeAndXref(
            BrAPIListTypes type,
            String xrefSource,
            String xrefId,
            Program program) throws ApiException, DoesNotExistException, ClassNotFoundException {
        BrAPIListSearchRequest searchRequest = new BrAPIListSearchRequest();
        if (type != null) {
            searchRequest.listType(type);
        }
        if (xrefSource != null && !xrefSource.isEmpty()) {
            searchRequest.externalReferenceSources(List.of(xrefSource));
        }
        if (xrefId != null && !xrefId.isEmpty()) {
            searchRequest.externalReferenceIDs(List.of(xrefId));
        }
        List<BrAPIListSummary> lists = listDAO.getListsBySearch(searchRequest, program.getId());
        if (lists == null) {
            throw new DoesNotExistException("list not returned from BrAPI service");
        }

        List<BrAPIListSummary> programLists = lists.stream().filter(list -> {
            Optional<BrAPIExternalReference> programXrefOptional = Utilities.getExternalReference(list.getExternalReferences(),Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS));
            return programXrefOptional.isPresent() && programXrefOptional.get().getReferenceID().equals(program.getId().toString());
        }).collect(Collectors.toList());

        // Map of <listDbId, GermplasmName> pairs.
        HashMap<String, String> itemsFromEachList = new HashMap<>();
        for (BrAPIListSummary list: programLists) {
            // remove the program key from the list name
            list.setListName(Utilities.removeProgramKeyAndUnknownAdditionalData(list.getListName(), program.getKey()));
            // set the owner of the list items as the list owner
            BrAPIListsSingleResponse listDetails = listDAO.getListById(list.getListDbId(), program.getId());
            // Add first item from list to hashmap.
            itemsFromEachList.put(list.getListDbId(), listDetails.getResult().getData().get(0));
        }
        if (type == BrAPIListTypes.GERMPLASM) {
            // Fetch one germplasm for each list from cache.
            List<BrAPIGermplasm> germplasmRepresentatives = germplasmDAO.getGermplasmByRawName((new ArrayList<>(itemsFromEachList.values())), program.getId());
            // Build hashmap of germplasm by name.
            HashMap<String, BrAPIGermplasm> germplasmByName = new HashMap<>();
            for (BrAPIGermplasm germplasm: germplasmRepresentatives) {
                germplasmByName.put(germplasm.getGermplasmName(), germplasm);
            }
            // For each list, set list owner name from createdBy stored in germplasm additional info.
            for (BrAPIListSummary list: programLists) {
                String strippedName = Utilities.removeProgramKeyAnyAccession(itemsFromEachList.get(list.getListDbId()), program.getKey());

                list.setListOwnerName(
                        germplasmByName.get(strippedName)
                                .getAdditionalInfo()
                                .getAsJsonObject("createdBy")
                                .get("userName")
                                .getAsString()
                );
            }
        }

        return programLists;
    }

    public HttpResponse<String> deleteBrAPIList(String listDbId, UUID programId, boolean hardDelete) throws ApiException {
        return listDAO.deleteBrAPIList(listDbId, programId, hardDelete);
    }
}
