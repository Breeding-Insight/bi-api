package org.breedinginsight.services;

import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.base.daos.ImportDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BrAPIGermplasmServiceUnitTest {
    private BrAPIListDAO listDAO;
    private BrAPIGermplasmDAO germplasmDAO;
    private ProgramService programService;
    private ProgramDAO programDAO;
    private BrAPIGermplasmService germplasmService;

    @BeforeEach
    void setup() {
        //Set up mocks in here
        listDAO = mock(BrAPIListDAO.class);
        programDAO = mock(ProgramDAO.class);
        germplasmDAO = new BrAPIGermplasmDAO(programDAO, mock(ImportDAO.class));
        programService = mock(ProgramService.class);
    }

    //TODO: Refactoring in BI-1443 needs to be done before this can be enabled due to BrAPIDAOUtil.search currently being static
    /*
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
        ProgramService programSpy = spy(programService);

        when(programDAO.getAll()).thenReturn(Arrays.asList(Program.builder().id(UUID.randomUUID()).name("Test Program").build()));

        doReturn(listResponse).when(brAPIListSpy).getListById(listId, testProgramId);
        MockedStatic<BrAPIDAOUtil> brapiDaoUtilMock = mockStatic(BrAPIDAOUtil.class);
        brapiDaoUtilMock.when(() -> BrAPIDAOUtil.search(any(Function.class),
                                                  any(Function3.class),
                                                  any(BrAPIGermplasmSearchRequest.class))).thenReturn(germplasm);
        doReturn(Optional.of(testProgram)).when(programSpy).getById(testProgramId);

        Method setupMethod = BrAPIGermplasmDAO.class.getDeclaredMethod("setup");
        setupMethod.setAccessible(true);
        setupMethod.invoke(germplasmDAO);

        germplasmService = new BrAPIGermplasmService(brAPIListSpy, programSpy, germplasmDAO);

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
     */
}
