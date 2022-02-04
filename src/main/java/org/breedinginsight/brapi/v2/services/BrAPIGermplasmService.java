package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.Pedigree;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.germplasm.GermplasmFileColumns;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.utilities.BrAPIDAOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Singleton
public class BrAPIGermplasmService {

    private BrAPIProvider brAPIProvider;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;
    private final String BREEDING_METHOD_ID_KEY = "breedingMethodId";
    private final String GERMPLASM_NAME_REGEX = "^(.*\\b) \\[([A-Z]{2,6})-(\\d+)\\]$";
    private ProgramService programService;
    private BrAPIListDAO brAPIListDAO;
    private BrAPIGermplasmDAO brAPIGermplasmDAO;

    @Inject
    public BrAPIGermplasmService(BrAPIProvider brAPIProvider, BrAPIListDAO brAPIListDAO, ProgramService programService, BrAPIGermplasmDAO brAPIGermplasmDAO) {
        this.brAPIProvider = brAPIProvider;
        this.brAPIListDAO = brAPIListDAO;
        this.programService = programService;
        this.brAPIGermplasmDAO =  brAPIGermplasmDAO;
    }

    //one option second method for getting list of germplasm ids list of germplasm, getbyprogramid, getbylistid
    // call to private method with processing code
    //one option optional list id to limit to list
    //try chunking first, more maintainable, clearer what public facing methods do
    // germplasmDBiD param to pipe into search DBID field
    public List<BrAPIGermplasm> getGermplasm(UUID programId) {
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);

        // Set query params and make call
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(Arrays.asList(programId.toString()));
        germplasmSearch.externalReferenceSources(Arrays.asList(String.format("%s/programs", referenceSource)));
        List<BrAPIGermplasm> germplasmList;
        try {
            germplasmList = BrAPIDAOUtil.search(
                    api::searchGermplasmPost,
                    api::searchGermplasmSearchResultsDbIdGet,
                    germplasmSearch
            );
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }

        return processGermplasmForDisplay(germplasmList);
    }

    private List<BrAPIGermplasm> processGermplasmForDisplay(List<BrAPIGermplasm> germplasmList) {
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

            BrAPIListsListResponse germplasmLists = brAPIListDAO.getListByTypeAndExternalRef(BrAPIListTypes.GERMPLASM, programId, referenceSource + "/programs", programId);

            //Remove key appended to listName for brapi
            String listName;
            String newListName;
            int listLength = germplasmLists.getResult().getData().size();
            for (int i=0; i<listLength; i++) {
                listName = germplasmLists.getResult().getData().get(i).getListName();
                newListName = removeAppendedKey(listName, program.getKey());
                germplasmLists.getResult().getData().get(i).setListName(newListName);
            }

            return germplasmLists;
        }
        else {
            throw new DoesNotExistException("Program does not exist");
        }
    }

    public HttpResponse<StreamedFile> exportGermplasmList(UUID programId, String listId) throws ApiException, IOException {
        List<Column> columns = GermplasmFileColumns.getOrderedColumns();

        //Retrieve germplasm list data
        BrAPIListDetails listData = brAPIListDAO.getListById(listId, programId).getResult();

        //Retrieve germplasm data
        List germplasmNames = listData.getData();
        List<BrAPIGermplasm> germplasm = processGermplasmForDisplay(brAPIGermplasmDAO.getGermplasmByName(germplasmNames, programId));
        germplasm.sort(Comparator.comparingInt(g -> g.getAdditionalInfo().get("importEntryNumber").getAsInt()));

        //TODO change timestamp to edit date when editing functionality is added
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-mm-dd:hh:mm:ss+hh:mm");
        String timestamp = formatter.format(listData.getDateCreated());

        String listName = listData.getListName();
        Optional<Program> optionalProgram = programService.getById(programId);
        if (optionalProgram.isPresent()) {
            Program program = optionalProgram.get();
            listName = removeAppendedKey(listName, program.getKey());
        }
        String fileName = listName+"_"+timestamp;

        //Convert list data to List<Map<String, Object>> data to pass into file writer
        List<Map<String, Object>> processedData =  new ArrayList<>();

        for (BrAPIGermplasm germplasmEntry: germplasm) {
            HashMap row = new HashMap<>();
            row.put("Name", germplasmEntry.getGermplasmName());
            row.put("Entry No", germplasmEntry.getAdditionalInfo().get("importEntryNumber").getAsDouble());
            row.put("Breeding Method", germplasmEntry.getAdditionalInfo().get("breedingMethod").getAsString());
            String source = germplasmEntry.getSeedSource();
            row.put("Source", source);

            //If germplasm was imported with an external UID, it will be stored in external reference with same source as seed source
            List<BrAPIExternalReference> externalReferences = germplasmEntry.getExternalReferences();
            for (BrAPIExternalReference reference: externalReferences){
                if (reference.getReferenceSource().equals(source)) {
                    row.put("External UID", reference.getReferenceID());
                    break;
                }
            }

            if (germplasmEntry.getPedigree() != null) {
                Pedigree germPedigree = Pedigree.parsePedigreeString(germplasmEntry.getPedigree());
                row.put("Female Parent GID", Double.parseDouble(germPedigree.femaleParent));
                row.put("Male Parent GID", Double.parseDouble(germPedigree.maleParent));
            }
            processedData.add(row);
        }

        StreamedFile downloadFile = ExcelWriter.writeToDownload("Germplasm Import", columns, processedData);

        return HttpResponse.ok(downloadFile).header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+fileName+".xlsx");
    }

    //Helper method to remove appended key from germplasm lists
    private String removeAppendedKey(String listName, String programKey){
        String appendedKey = String.format(" [%s-germplasm]", programKey);
        return listName.replace(appendedKey, "");
    }


}
