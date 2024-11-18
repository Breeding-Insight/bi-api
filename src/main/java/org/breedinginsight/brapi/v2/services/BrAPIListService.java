package org.breedinginsight.brapi.v2.services;

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListSearchRequest;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.delta.DeltaListDetails;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
        List<BrAPIListSummary> lists = listDAO.getListBySearch(searchRequest, program.getId());
        if (lists == null) {
            throw new DoesNotExistException("list not returned from BrAPI service");
        }

        List<BrAPIListSummary> programLists = lists.stream().filter(list -> {
            Optional<BrAPIExternalReference> programXrefOptional = Utilities.getExternalReference(list.getExternalReferences(),Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS));
            return programXrefOptional.isPresent() && programXrefOptional.get().getReferenceID().equals(program.getId().toString());
        }).collect(Collectors.toList());
        for (BrAPIListSummary list: programLists) {

            // remove the program key from the list name
            list.setListName(Utilities.removeProgramKeyAndUnknownAdditionalData(list.getListName(), program.getKey()));

            // set the owner of the list items as the list owner
            BrAPIListsSingleResponse listDetails = listDAO.getListById(list.getListDbId(), program.getId());
            List<String> listItemNames = listDetails.getResult().getData();
            if (type != null) {
                switch (type) {
                    case GERMPLASM:
                        String createdBy = germplasmDAO.getGermplasmByRawName(listItemNames, program.getId()).get(0)
                                .getAdditionalInfo()
                                .getAsJsonObject("createdBy")
                                .get("userName")
                                .getAsString();
                        list.setListOwnerName(createdBy);
                    case OBSERVATIONVARIABLES:
                    default:
                        break;
                }
            }
        }

        return programLists;
    }

    public DeltaListDetails getDeltaListDetails(String listDbId, UUID programId) throws ApiException {
        return listDAO.getDeltaListDetailsByDbId(listDbId, programId);
    }

    public void deleteBrAPIList(String listDbId, UUID programId, boolean hardDelete) throws ApiException {
        listDAO.deleteBrAPIList(listDbId, programId, hardDelete);
    }
}
