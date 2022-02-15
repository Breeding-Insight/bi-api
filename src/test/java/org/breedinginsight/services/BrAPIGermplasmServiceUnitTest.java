package org.breedinginsight.services;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.parsers.germplasm.GermplasmFileColumns;
import org.breedinginsight.utilities.FileUtil;
import org.junit.jupiter.api.*;
import tech.tablesaw.api.Table;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BrAPIGermplasmServiceUnitTest {
    private BrAPIListDAO listDAO;
    private BrAPIGermplasmDAO germplasmDAO;
    private ProgramService programService;
    private BrAPIGermplasmService germplasmService;

    @BeforeEach
    void setup() {
        //Set up mocks in here
        listDAO = mock(BrAPIListDAO.class);
        germplasmDAO = mock(BrAPIGermplasmDAO.class);
        programService = mock(ProgramService.class);
    }

    @Test
    @SneakyThrows
    public void getGermplasmListExport() {
        //Create Program
        Program testProgram = new Program();
        UUID testProgramId = UUID.randomUUID();
        testProgram.setName("Test Program");
        testProgram.setKey("TEST");
        testProgram.setId(testProgramId);

        //Create List
        BrAPIListsSingleResponse listResponse = new BrAPIListsSingleResponse();
        String listId = "1";
        String listName = "List Name";
        List<String> germplasmNames = Arrays.asList("Germplasm A", "Germplasm B");
        BrAPIListDetails listDetails = new BrAPIListDetails();
        listDetails.setData(germplasmNames);
        listDetails.setListName(listName + " [TEST-germplasm]");
        listDetails.setListDescription("List Description");
        listDetails.setListDbId(listId);
        OffsetDateTime dateTime = OffsetDateTime.now();
        listDetails.setDateCreated(dateTime);
        listResponse.setResult(listDetails);

        //Create Germplasm
        List<BrAPIGermplasm> germplasm = new ArrayList();
        BrAPIGermplasm testGermplasm = new BrAPIGermplasm();
        testGermplasm.setGermplasmName("Germplasm A [TEST-1]");
        testGermplasm.setSeedSource("Wild");
        testGermplasm.setAccessionNumber("1");
        testGermplasm.setDefaultDisplayName("Germplasm A");
        JsonObject additionalInfo = new JsonObject();
        additionalInfo.addProperty("importEntryNumber", "2");
        additionalInfo.addProperty("breedingMethod", "Allopolyploid");
        testGermplasm.setAdditionalInfo(additionalInfo);
        List<BrAPIExternalReference> externalRef = new ArrayList<>();
        BrAPIExternalReference testReference = new BrAPIExternalReference();
        testReference.setReferenceSource("TestSource");
        externalRef.add(testReference);
        testGermplasm.setExternalReferences(externalRef);
        germplasm.add(testGermplasm);

        testGermplasm = new BrAPIGermplasm();
        testGermplasm.setGermplasmName("Germplasm B [TEST-2]");
        testGermplasm.setSeedSource("Cultivated");
        testGermplasm.setAccessionNumber("2");
        testGermplasm.setDefaultDisplayName("Germplasm B");
        testGermplasm.setPedigree("Germplasm A [TEST-1]");
        additionalInfo = new JsonObject();
        additionalInfo.addProperty("importEntryNumber", "3");
        additionalInfo.addProperty("breedingMethod", "Autopolyploid");
        testGermplasm.setAdditionalInfo(additionalInfo);
        testGermplasm.setExternalReferences(externalRef);
        germplasm.add(testGermplasm);

        //Create stubs
        BrAPIListDAO brAPIListSpy = spy(listDAO);
        BrAPIGermplasmDAO brAPIGermplasmSpy = spy(germplasmDAO);
        ProgramService programSpy = spy(programService);

        doReturn(listResponse).when(brAPIListSpy).getListById(listId, testProgramId);
        doReturn(germplasm).when(brAPIGermplasmSpy).getGermplasmByName(germplasmNames, testProgramId);
        doReturn(Optional.of(testProgram)).when(programSpy).getById(testProgramId);

        germplasmService = new BrAPIGermplasmService(brAPIListSpy, programSpy, brAPIGermplasmSpy);

        //Retrieve file
        DownloadFile downloadFile = germplasmService.exportGermplasmList(testProgramId, listId);
        InputStream inputStream = downloadFile.getStreamedFile().getInputStream();
        Table resultTable = FileUtil.parseTableFromExcel(inputStream, 0);

        //Format Date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
        String timestamp = formatter.format(dateTime);

        List<String> expectedColumnNames = GermplasmFileColumns.getOrderedColumns().stream().map(c -> c.getValue()).collect(Collectors.toList());

        //Check file values
        assertEquals(listName+"_"+timestamp, downloadFile.getFileName(), "Incorrect export file name");
        assertEquals(2, resultTable.rowCount(), "Wrong number of rows were exported");
        assertEquals(expectedColumnNames, resultTable.columnNames(), "Incorrect columns were exported");
        assertEquals("Germplasm A", resultTable.get(0, 1), "Incorrect data exported");
        assertEquals("2", resultTable.get(0, 6), "Incorrect data exported");
    }
}
