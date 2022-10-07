package org.breedinginsight.daos;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.v2.model.*;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponseResult;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BrAPIDAOUtilUnitTest {
    private String referenceSource;
    private BrAPIDAOUtil brAPIDAOUtil;
    private List<BrAPIGermplasm> germplasm;
    private Program testProgram;
    private List<BrAPIGermplasm>  paginatedGermplasm;
    private BrAPIGermplasmSearchRequest germplasmSearch;

    public Integer fetchPaginatedGermplasm(int page, int pageSize) {
        paginatedGermplasm = new ArrayList<>();
        int pageStart = page * pageSize;
        int pageEnd = pageStart + pageSize;
        Integer totalPages = (int) Math.ceil(germplasm.size() / pageSize);
        if (!(pageEnd  > germplasm.size())) {
            for (int i = pageStart; i < pageEnd; i++) {
                paginatedGermplasm.add(germplasm.get(i));
            }
        } else {
            paginatedGermplasm = germplasm;
        }
        return totalPages;
    }

    public ApiResponse<Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>>> getStubbedGermplasm(int page, int pageSize) {
        Integer totalPages = fetchPaginatedGermplasm(page, pageSize);
        BrAPIGermplasmListResponse searchPostResponse = new BrAPIGermplasmListResponse();
        searchPostResponse.setResult(new BrAPIGermplasmListResponseResult().data(paginatedGermplasm));
        searchPostResponse.setMetadata(new BrAPIMetadata().pagination(
                (BrAPIIndexPagination) new BrAPIIndexPagination().currentPage(page).pageSize(pageSize).totalPages(totalPages)));

        Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>> searchPostResponsePair =
                Pair.of(Optional.of(searchPostResponse), Optional.empty());
        return new ApiResponse<Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>>>(200, new HashMap<>(), searchPostResponsePair);
    }

    @SneakyThrows
    @BeforeEach
    void setup() {
        //Create instance of DAO
        brAPIDAOUtil = new BrAPIDAOUtil();

        //Set the page size field
        Field pageSize = BrAPIDAOUtil.class.getDeclaredField("pageSize");
        pageSize.setAccessible(true);
        pageSize.set(brAPIDAOUtil, 1);

        referenceSource = "breedinginsight.org";

        //Create Program
        testProgram = new Program();
        testProgram.setName("Test Program");
        testProgram.setKey("TEST");
        testProgram.setId(UUID.randomUUID());
        testProgram.setBrapiUrl("http://brapiserver:8083");

        //Create Germplasm
        List<String> shortNames = new ArrayList<String>();
        shortNames.add("A");
        shortNames.add("B");
        shortNames.add("C");
        shortNames.add("D");
        shortNames.add("E");
        shortNames.add("F");
        shortNames.add("G");
        shortNames.add("H");
        shortNames.add("I");
        shortNames.add("J");

        germplasm = new ArrayList();

        for (int i = 0; i < shortNames.size(); i++) {
            String entry = String.valueOf(i + 2);
            String nameIndex = String.valueOf(i+1);
            BrAPIGermplasm testGermplasm = new BrAPIGermplasm();
            testGermplasm.setGermplasmName(String.format("Germplasm %1$s [TEST-%2$s]",
                    shortNames.get(i), nameIndex));
            testGermplasm.setSeedSource("Wild");
            testGermplasm.setAccessionNumber(nameIndex);
            testGermplasm.setDefaultDisplayName(String.format("Germplasm %s", shortNames.get(i)));
            if(!shortNames.get(i).equals("A")) {
                testGermplasm.setPedigree("Germplasm A [TEST-1]");
            }
            JsonObject additionalInfo = new JsonObject();
            additionalInfo.addProperty("importEntryNumber", entry);
            additionalInfo.addProperty("breedingMethod", "Allopolyploid");
            testGermplasm.setAdditionalInfo(additionalInfo);
            List<BrAPIExternalReference> externalRef = new ArrayList<>();
            BrAPIExternalReference testReference = new BrAPIExternalReference();
            testReference.setReferenceSource(referenceSource);
            testReference.setReferenceID(UUID.randomUUID().toString());
            externalRef.add(testReference);
            testGermplasm.setExternalReferences(externalRef);
            germplasm.add(testGermplasm);
        }

        // Set query params
        germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(List.of(testProgram.getId().toString()));
        germplasmSearch.externalReferenceSources(List.of(String.format("%s/programs", referenceSource)));
    }

    @Test
    @SneakyThrows
    public void searchGermplasmPost() {
        // Set query params
        germplasmSearch.setPage(0);

        List<BrAPIGermplasm> searchResult = brAPIDAOUtil.search(
                searchBody -> {
                  return getStubbedGermplasm(searchBody.getPage(), searchBody.getPageSize());
                },
                (searchId, page, pageSize) -> {
                    return null;
                },
                germplasmSearch
        );

        //Check if all requested germplasm are returned
        for (BrAPIGermplasm accession : germplasm) {
            assert searchResult.stream().anyMatch(
                    result -> result.getExternalReferences().get(0).getReferenceID().equals(
                            accession.getExternalReferences().get(0).getReferenceID()
                    ));
        }
    }

    @Test
    @SneakyThrows
    public void searchGermplasmDbIdGet() {
        List<BrAPIGermplasm> searchResult = brAPIDAOUtil.search(
                searchBody -> {
                    BrAPIAcceptedSearchResponse searchPostResponse = new BrAPIAcceptedSearchResponse();
                    searchPostResponse.setResult(new BrAPIAcceptedSearchResponseResult().searchResultsDbId(UUID.randomUUID().toString()));

                    Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>> searchPostResponsePair =
                            Pair.of(Optional.empty(), Optional.of(searchPostResponse));
                    return new ApiResponse<Pair<Optional<BrAPIGermplasmListResponse>, Optional<BrAPIAcceptedSearchResponse>>>(202, new HashMap<>(), searchPostResponsePair);

                },
                (searchId, page, pageSize) -> {
                    return getStubbedGermplasm(page, pageSize);
                },
                germplasmSearch
        );

        //Check if all germplasm are returned
        for (BrAPIGermplasm accession : germplasm) {
            assert searchResult.stream().anyMatch(
                    result -> result.getExternalReferences().get(0).getReferenceID().equals(
                            accession.getExternalReferences().get(0).getReferenceID()
                    ));
        }
    }
}
