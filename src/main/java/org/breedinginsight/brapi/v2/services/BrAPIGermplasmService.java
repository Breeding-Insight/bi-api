package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Pedigree;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.germplasm.GermplasmFileColumns;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.services.brapi.BrAPIClientProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
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

    public List<BrAPIGermplasm> getGermplasm(UUID programId) throws ApiException {

        // Set query params and make call
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(Arrays.asList(programId.toString()));
        germplasmSearch.externalReferenceSources(Arrays.asList(String.format("%s/programs", referenceSource)));
        List<BrAPIGermplasm> germplasmList;
        try {
            germplasmList = germplasmDAO.getGermplasm(programId);
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }

        return processGermplasmForDisplay(germplasmList, programId);
    }

    private List<BrAPIGermplasm> processGermplasmForDisplay(List<BrAPIGermplasm> germplasmList, UUID programId) throws ApiException {
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
                if (parents.size() >= 1) {
                    if (germplasmByFullName.containsKey(parents.get(0))) {
                    //problem, if parent not in germplasmbyfullname, not replaced by accession number, assumption that parents are also in file
                    newPedigreeString = germplasmByFullName.get(parents.get(0)).getAccessionNumber();}
                    else {
                        //Try to find parent in program germplasm
                        List<String> parentName = Arrays.asList(parents.get(0));
                        List<BrAPIGermplasm> parentList = germplasmDAO.getGermplasmByName(parentName, programId);
                        if (parentList.size() > 0) {
                            BrAPIGermplasm parentGermplasm = parentList.get(0);
                            newPedigreeString = parentGermplasm.getAccessionNumber();
                        }
                    }
                }
                if (parents.size() == 2) {
                    if (germplasmByFullName.containsKey(parents.get(1))) {
                        newPedigreeString += "/" + germplasmByFullName.get(parents.get(1)).getAccessionNumber();
                    }
                    else {
                        //Try to find parent in program germplasm
                        List<String> parentName = Arrays.asList(parents.get(1));
                        List<BrAPIGermplasm> parentList = germplasmDAO.getGermplasmByName(parentName, programId);
                        if (parentList.size() > 0) {
                            BrAPIGermplasm parentGermplasm = parentList.get(0);
                            newPedigreeString += "/" +  parentGermplasm.getAccessionNumber();
                        }
                    }
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

    public DownloadFile exportGermplasmList(UUID programId, String listId) throws ApiException, IOException {
        List<Column> columns = GermplasmFileColumns.getOrderedColumns();

        //Retrieve germplasm list data
        BrAPIListDetails listData = brAPIListDAO.getListById(listId, programId).getResult();

        //Retrieve germplasm data
        List germplasmNames = listData.getData();
        List<BrAPIGermplasm> germplasm = processGermplasmForDisplay(germplasmDAO.getGermplasmByName(germplasmNames, programId), programId);
        //processGermplasmForDisplay, numbers
        germplasm.sort(Comparator.comparingInt(g -> g.getAdditionalInfo().get("importEntryNumber").getAsInt()));

        //TODO change timestamp to edit date when editing functionality is added
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
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
            row.put("GID", Double.valueOf(germplasmEntry.getAccessionNumber()));
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
                if (!germPedigree.maleParent.isEmpty()) row.put("Male Parent GID", Double.parseDouble(germPedigree.maleParent));
            }
            processedData.add(row);
        }

        StreamedFile downloadFile = ExcelWriter.writeToDownload("Germplasm Import", columns, processedData);

        return new DownloadFile(fileName, downloadFile);
    }

    //Helper method to remove appended key from germplasm lists
    private String removeAppendedKey(String listName, String programKey){
        String appendedKey = String.format(" [%s-germplasm]", programKey);
        return listName.replace(appendedKey, "");
    }

    public List<BrAPIGermplasm> importBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList, UUID programId, ImportUpload upload) throws ApiException {
        return germplasmDAO.importBrAPIGermplasm(brAPIGermplasmList, programId, upload);
    }

    public List<BrAPIGermplasm> getRawGermplasmByAccessionNumber(ArrayList<String> germplasmAccessionNumbers, UUID programId) throws ApiException {
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
    
    public List<BrAPIGermplasm> getGermplasmByDisplayName(List<String> germplasmDisplayNames, UUID programId) throws ApiException {
        List<BrAPIGermplasm> allGermplasm = getGermplasm(programId);
        HashSet<String> requestedNames = new HashSet<>(germplasmDisplayNames);
        List<BrAPIGermplasm> matchingGermplasm = new ArrayList<>();
        for (BrAPIGermplasm germplasm: allGermplasm) {
            if (requestedNames.contains(germplasm.getDefaultDisplayName())) {
                matchingGermplasm.add(germplasm);
            }
        }
        return matchingGermplasm;
    }
}
