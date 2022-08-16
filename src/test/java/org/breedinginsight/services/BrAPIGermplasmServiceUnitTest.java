package org.breedinginsight.services;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.parsers.germplasm.GermplasmFileColumns;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import tech.tablesaw.api.Table;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private ProgramDAO programDAO;
    private BrAPIGermplasmService germplasmService;
    private BrAPIDAOUtil brAPIDAOUtil;
    private String referenceSource;

    @SneakyThrows
    @BeforeEach
    void setup() {
        //Set up mocks in here
        referenceSource = "breedinginsight.org";
        listDAO = mock(BrAPIListDAO.class);
        programDAO = mock(ProgramDAO.class);
        brAPIDAOUtil = mock(BrAPIDAOUtil.class);
        germplasmDAO = new BrAPIGermplasmDAO(programDAO, mock(ImportDAO.class), brAPIDAOUtil);
        programService = mock(ProgramService.class);

        Field externalReferenceSource = BrAPIGermplasmDAO.class.getDeclaredField("referenceSource");
        externalReferenceSource.setAccessible(true);
        externalReferenceSource.set(germplasmDAO, referenceSource);
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
        List<String> germplasmNames = Arrays.asList("Germplasm A [TEST-1]", "Germplasm B [TEST-2]");
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
        testReference.setReferenceSource(referenceSource);
        testReference.setReferenceID(UUID.randomUUID().toString());
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
        testReference = new BrAPIExternalReference();
        testReference.setReferenceSource(referenceSource);
        testReference.setReferenceID(UUID.randomUUID().toString());
        externalRef = new ArrayList<>();
        externalRef.add(testReference);
        testGermplasm.setExternalReferences(externalRef);
        germplasm.add(testGermplasm);

        //Stub out the spies
        BrAPIListDAO brAPIListSpy = spy(listDAO);
        doReturn(listResponse).when(brAPIListSpy).getListById(listId, testProgramId);
        ProgramService programSpy = spy(programService);
        doReturn(Optional.of(testProgram)).when(programSpy).getById(testProgramId);

        //Stub out the mocks
        when(programDAO.getAll()).thenReturn(Arrays.asList(Program.builder().id(testProgramId).name("Test Program").active(true).build()));
        when(programDAO.fetchOneById(any(UUID.class))).thenReturn(testProgram);
        when(programDAO.get(any(UUID.class))).thenReturn(Arrays.asList(testProgram));
        when(brAPIDAOUtil.search(any(Function.class),
                                     any(Function3.class),
                                     any(BrAPIGermplasmSearchRequest.class))).thenReturn(germplasm);

        //Create germplasm cache of stub data
        Method setupMethod = BrAPIGermplasmDAO.class.getDeclaredMethod("setup");
        setupMethod.setAccessible(true);
        setupMethod.invoke(germplasmDAO);

        //Create test instance of service, injecting spy- and mock-dependencies
        germplasmService = new BrAPIGermplasmService(brAPIListSpy, programSpy, germplasmDAO);

        //Retrieve file
        DownloadFile downloadFile = germplasmService.exportGermplasmList(testProgramId, listId, FileType.XLS);
        InputStream inputStream = downloadFile.getStreamedFile().getInputStream();
        Table resultTable = FileUtil.parseTableFromExcel(inputStream, 0);

        //Format Date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
        String timestamp = formatter.format(dateTime);

        List<String> expectedColumnNames = GermplasmFileColumns.getOrderedColumns().stream().map(c -> c.getValue()).collect(Collectors.toList());

        //Check file values
        assertEquals(listName+"_"+timestamp, downloadFile.getFileName(), "Incorrect export file name");
        assertEquals(expectedColumnNames, resultTable.columnNames(), "Incorrect columns were exported");
        assertEquals(2, resultTable.rowCount(), "Wrong number of rows were exported");
        // assertEquals("Germplasm A", resultTable.get(0, 1), "Incorrect data exported");
        // assertEquals("2", resultTable.get(0, 6), "Incorrect data exported");
    }
}
