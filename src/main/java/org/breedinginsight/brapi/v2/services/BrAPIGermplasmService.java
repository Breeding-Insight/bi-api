package org.breedinginsight.brapi.v2.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Pedigree;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.germplasm.GermplasmFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIGermplasmService {

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    private final BrAPIGermplasmDAO germplasmDAO;
    private final ProgramService programService;
    private final BrAPIListDAO brAPIListDAO;

    @Inject
    public BrAPIGermplasmService(BrAPIListDAO brAPIListDAO, ProgramService programService, BrAPIGermplasmDAO germplasmDAO) {
        this.brAPIListDAO = brAPIListDAO;
        this.programService = programService;
        this.germplasmDAO = germplasmDAO;
    }

    public List<BrAPIGermplasm> getGermplasm(UUID programId) throws ApiException {
        try {
            return germplasmDAO.getGermplasm(programId);
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    public BrAPIGermplasm getGermplasmByUUID(UUID programId, String germplasmId) throws DoesNotExistException {
        try {
            return germplasmDAO.getGermplasmByUUID(germplasmId, programId);
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    public BrAPIGermplasm getGermplasmByDBID(UUID programId, String germplasmId) throws DoesNotExistException {
        try {
            return germplasmDAO.getGermplasmByDBID(germplasmId, programId);
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    public List<BrAPIListSummary> getGermplasmListsByProgramId(UUID programId, HttpRequest<String> request) throws DoesNotExistException, ApiException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        Optional<Program> optionalProgram = programService.getById(programId);
        if(optionalProgram.isPresent()) {
            Program program = optionalProgram.get();

            List<BrAPIListSummary> germplasmLists = brAPIListDAO.getListByTypeAndExternalRef(BrAPIListTypes.GERMPLASM, programId, referenceSource + "/programs", programId);

            for (BrAPIListSummary germplasmList: germplasmLists) {
                String listName = germplasmList.getListName();
                String newListName = removeAppendedKey(listName, program.getKey());
                germplasmList.setListName(newListName);

                //Retrieve germplasm details to get list owner name
                //Due to listOwnerName not being stored in breedbase
                BrAPIListDetails listData = brAPIListDAO.getListById(germplasmList.getListDbId(), programId).getResult();
                List<String> germplasmNames = listData.getData().subList(0,1);
                List<BrAPIGermplasm> germplasm = germplasmDAO.getGermplasmByRawName(germplasmNames, programId);
                String createdBy = germplasm.get(0).getAdditionalInfo().getAsJsonObject("createdBy").get("userName").getAsString();
                germplasmList.setListOwnerName(createdBy);
            }

            return germplasmLists;
        }
        else {
            throw new DoesNotExistException("Program does not exist");
        }
    }

    public List<Map<String, Object>> processListData(List<BrAPIGermplasm> germplasm, UUID germplasmListId){
        List<Map<String, Object>> processedData =  new ArrayList<>();

        for (BrAPIGermplasm germplasmEntry: germplasm) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("GID", Integer.valueOf(germplasmEntry.getAccessionNumber()));
            row.put("Name", germplasmEntry.getGermplasmName());
            row.put("Breeding Method", germplasmEntry.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD).getAsString());
            String source = germplasmEntry.getSeedSource();
            row.put("Source", source);

            // Use the entry number in the list map if generated
            if(new UUID(0,0).compareTo(germplasmListId) == 0) {
                row.put("Entry No", germplasmEntry.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER).getAsInt());
            } else {
                row.put("Entry No", germplasmEntry.getAdditionalInfo()
                        .getAsJsonObject(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ENTRY_NUMBERS).get(germplasmListId.toString()).getAsInt());
            }

            //If germplasm was imported with an external UID, it will be stored in external reference with same source as seed source
            List<BrAPIExternalReference> externalReferences = germplasmEntry.getExternalReferences();
            for (BrAPIExternalReference reference: externalReferences){
                if (reference.getReferenceSource().equals(source)) {
                    row.put("External UID", reference.getReferenceID());
                    break;
                }
            }

            if ((germplasmEntry.getPedigree() != null) && (!germplasmEntry.getPedigree().isEmpty())) {
                Pedigree germPedigree = Pedigree.parsePedigreeString(germplasmEntry.getPedigree());
                if (!germPedigree.maleParent.isEmpty()) {
                    row.put("Male Parent GID", Integer.parseInt(germPedigree.maleParent));
                }
                if (!germPedigree.femaleParent.isEmpty()) {
                    row.put("Female Parent GID", Integer.parseInt(germPedigree.femaleParent));
                }
            }

            // Synonyms
            if (germplasmEntry.getSynonyms() != null && !germplasmEntry.getSynonyms().isEmpty()) {
                String joinedSynonyms = germplasmEntry.getSynonyms().stream()
                        .map(synonym -> synonym.getSynonym())
                        .collect(Collectors.joining(";"));
                row.put("Synonyms", joinedSynonyms);
            }

            processedData.add(row);
        }
        return processedData;
    }

    public List<BrAPIGermplasm> getGermplasmByList(UUID programId, String listDbId) throws ApiException {
        // get list germplasm names
        BrAPIListsSingleResponse listResponse = brAPIListDAO.getListById(listDbId, programId);
        if(Objects.nonNull(listResponse) && Objects.nonNull(listResponse.getResult())) {

            // get the list ID stored in the list external references
            UUID listId = getGermplasmListId(listResponse.getResult());

            // get list BrAPI germplasm variables
            List<String> germplasmNames = listResponse.getResult().getData();
            List<BrAPIGermplasm> germplasm = germplasmDAO.getGermplasmByRawName(germplasmNames, programId);

            // set the list ID in the germplasm additional info
             germplasm.forEach(g -> g.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ID, listId));
            return germplasm;
        } else throw new ApiException();
    }
    public DownloadFile exportGermplasmList(UUID programId, String listId, FileType fileExtension) throws IllegalArgumentException, ApiException, IOException {
        List<Column> columns = GermplasmFileColumns.getOrderedColumns();

        //Retrieve germplasm list data
        BrAPIListDetails listData = brAPIListDAO.getListById(listId, programId).getResult();

        //Retrieve germplasm data
        List<String> germplasmNames = listData.getData();
        List<BrAPIGermplasm> germplasm = germplasmDAO.getGermplasmByRawName(germplasmNames, programId);

        //processGermplasmForDisplay, numbers
        UUID germplasmListId = getGermplasmListId(listData);
        germplasm.sort(Comparator.comparingInt(getEntryNumber(germplasmListId)));

        String listName = listData.getListName();
        Optional<Program> optionalProgram = programService.getById(programId);
        if (optionalProgram.isPresent()) {
            Program program = optionalProgram.get();
            listName = removeAppendedKey(listName, program.getKey());
        }
        String fileName = createFileName(listData, listName);
        StreamedFile downloadFile;
        //Convert list data to List<Map<String, Object>> data to pass into file writer
        List<Map<String, Object>> processedData =  processListData(germplasm, germplasmListId);

        if (fileExtension == FileType.CSV){
            downloadFile = CSVWriter.writeToDownload(columns, processedData, fileExtension);
        } else {
            downloadFile = ExcelWriter.writeToDownload("Germplasm Import", columns, processedData, fileExtension);
        }

        return new DownloadFile(fileName, downloadFile);
    }

    public UUID getGermplasmListId(BrAPIListDetails listData) {
        if(Objects.nonNull(listData.getExternalReferences()) && hasListExternalReference(listData.getExternalReferences())) {
            return UUID.fromString(listData.getExternalReferences().stream()
                    .filter(e -> referenceSource.concat("/lists").equals(e.getReferenceSource()))
                    .map(e -> e.getReferenceID()).findAny().orElse("00000000-0000-0000-000000000000"));
        } else {
            return new UUID(0,0);
        }
    }

    public UUID getGermplasmListId(BrAPIListNewRequest importList) {
        if(Objects.nonNull(importList.getExternalReferences()) && hasListExternalReference(importList.getExternalReferences())) {
            return UUID.fromString(importList.getExternalReferences().stream()
                    .filter(e -> referenceSource.concat("/lists").equals(e.getReferenceSource()))
                    .map(e -> e.getReferenceID()).findAny().orElse("00000000-0000-0000-000000000000"));
        } else {
            return new UUID(0,0);
        }
    }

    private boolean hasListExternalReference(List<BrAPIExternalReference> refs) throws IllegalArgumentException {
        if (refs == null) throw new IllegalArgumentException();
        return refs.stream().anyMatch(e -> referenceSource.concat("/lists").equals(e.getReferenceSource()));
    }

    private ToIntFunction<BrAPIGermplasm> getEntryNumber(UUID germplasmListId) throws IllegalArgumentException {
        if(germplasmListId.compareTo(new UUID(0,0)) == 0) {
            return this::getImportEntryNumber;
        } else {
            return g -> getGermplasmListEntryNumber(g, germplasmListId);
        }
    }

    private Integer getImportEntryNumber(BrAPIGermplasm g) throws IllegalArgumentException {
        if(Objects.nonNull(g.getAdditionalInfo()) &&
                g.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER)) {
            return g.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER).getAsInt();
        } else {
            throw new IllegalArgumentException();
        }
    }
    private Integer getGermplasmListEntryNumber(BrAPIGermplasm g, UUID germplasmListId) throws IllegalArgumentException {
        if(Objects.nonNull(g.getAdditionalInfo()) &&
                g.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ENTRY_NUMBERS)) {
            return g.getAdditionalInfo().getAsJsonObject(BrAPIAdditionalInfoFields.GERMPLASM_LIST_ENTRY_NUMBERS)
                .get(germplasmListId.toString()).getAsInt();
        } else {
            throw new IllegalArgumentException();
        }
    }

    private String createFileName(BrAPIListDetails listData, String listName) {
        //TODO change timestamp to edit date when editing functionality is added
        String timestamp;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");

        //TODO this logic statement may not be appropriate whe the edit date is used (see the TODO note above)
        if(listData.getDateCreated() == null) {
            timestamp = formatter.format(OffsetDateTime.now());
        }
        else{
            timestamp = formatter.format(listData.getDateCreated());
        }
        String fileName = listName +"_"+timestamp;
        return fileName;
    }

    //Helper method to remove appended key from germplasm lists
    private String removeAppendedKey(String listName, String programKey){
        String appendedKey = String.format(" [%s-germplasm]", programKey);
        return listName.replace(appendedKey, "");
    }

    public List<BrAPIGermplasm> importBrAPIGermplasm(List<BrAPIGermplasm> postBrAPIGermplasmList, List<BrAPIGermplasm> putBrAPIGermplasmList,
                                                     UUID programId, ImportUpload upload) throws ApiException {
        return germplasmDAO.importBrAPIGermplasm(postBrAPIGermplasmList, putBrAPIGermplasmList, programId, upload);
    }

    public List<BrAPIGermplasm> getRawGermplasmByAccessionNumber(ArrayList<String> germplasmAccessionNumbers, UUID programId) throws ApiException {
        List<BrAPIGermplasm> germplasmList = germplasmDAO.getRawGermplasm(programId);
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
