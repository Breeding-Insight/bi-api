package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Slf4j
@Singleton
public class BrAPIGermplasmService {

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    private BrAPIGermplasmDAO germplasmDAO;
    private final String BREEDING_METHOD_ID_KEY = "breedingMethodId";
    private ProgramService programService;
    private BrAPIListDAO brAPIListDAO;

    @Inject
    public BrAPIGermplasmService(BrAPIListDAO brAPIListDAO, ProgramService programService, BrAPIGermplasmDAO germplasmDAO) {
        this.brAPIListDAO = brAPIListDAO;
        this.programService = programService;
        this.germplasmDAO = germplasmDAO;
    }

    public List<BrAPIGermplasm> getGermplasm(UUID programId) {
        // Get germplasm
        List<BrAPIGermplasm> germplasmList;
        try {
            germplasmList = germplasmDAO.getGermplasm(programId);
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }

        // Process the germplasm
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

    public BrAPIListsListResponse getGermplasmListsByProgramId(UUID programId, HttpRequest<String> request) throws DoesNotExistException, ApiException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        Optional<Program> optionalProgram = programService.getById(programId);
        if(optionalProgram.isPresent()) {
            Program program = optionalProgram.get();
            String appendedKey = String.format(" [%s-germplasm]", program.getKey());

            BrAPIListsListResponse germplasmLists = brAPIListDAO.getListByTypeAndExternalRef(BrAPIListTypes.GERMPLASM, programId, referenceSource + "/programs", programId);

            //Remove key appended to listName for brapi
            String listName;
            String newListName;
            int listLength = germplasmLists.getResult().getData().size();
            for (int i=0; i<listLength; i++) {
                listName = germplasmLists.getResult().getData().get(i).getListName();
                newListName = listName.replace(appendedKey, "");
                germplasmLists.getResult().getData().get(i).setListName(newListName);
            }

            return germplasmLists;
        }
        else {
            throw new DoesNotExistException("Program does not exist");
        }
    }

    public List<BrAPIGermplasm> importBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList, UUID programId, ImportUpload upload) throws ApiException {
        return germplasmDAO.importBrAPIGermplasm(brAPIGermplasmList, programId, upload);
    }

    public List<BrAPIGermplasm> getGermplasmByAccessionNumber(ArrayList<String> germplasmAccessionNumbers, UUID programId) throws ApiException {
        List<BrAPIGermplasm> germplasmList = germplasmDAO.getGermplasm(programId);
        List<BrAPIGermplasm> resultGermplasm = new ArrayList<>();
        // Search for accession number matches
        for (BrAPIGermplasm germplasm: germplasmList) {
            for (String accessionNumber: germplasmAccessionNumbers) {
                if (germplasm.getAccessionNumber().equals(accessionNumber)) {
                    resultGermplasm.add(germplasm);
                    break;
                }
            }
        }
        return resultGermplasm;
    }
}
