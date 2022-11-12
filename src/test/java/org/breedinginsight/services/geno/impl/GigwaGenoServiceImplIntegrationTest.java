package org.breedinginsight.services.geno.impl;

import com.agorapulse.micronaut.amazon.awssdk.s3.DefaultSimpleStorageService;
import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponseResult;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponseResult;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.daos.impl.ImportMappingDAOImpl;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.ImporterImportEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.daos.impl.ProgramDAOImpl;
import org.breedinginsight.daos.impl.UserDAOImpl;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.mockito.invocation.InvocationOnMock;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Property(name = "localstack.services", value = "s3")
@Property(name = "aws.s3.buckets.geno.bucket", value = "test")
public class GigwaGenoServiceImplIntegrationTest extends DatabaseTest {

    @Inject
    private ProgramDAO programDAO;

    @Inject
    private DSLContext dsl;
    @Inject
    private Configuration config;

    @Inject
    private BrAPIProvider brAPIProvider;

    @Inject
    private BrAPIClientProvider brAPIClientProvider;

    @Inject
    private GigwaGenoServiceImpl gigwaGenoStorageService;

    @Inject
    private UserDAO userDAO;

    @Inject
    private ImportMappingDAO importMappingDAO;

    @Inject
    private ImportDAO importDAO;

    @Inject
    private BrAPITrialDAO trialDAO;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    @Named("geno")
    private SimpleStorageService storageService;

    @Inject
    private S3Client s3Client;

    @Inject
    private S3Presigner presigner;

    @Inject
    private BrAPIDAOUtil brAPIDAOUtil;

    @Inject
    private BrAPIEndpointProvider brAPIEndpointProvider;

    @Property(name = "gigwa.host")
    private String gigwaHost;

    @Property(name = "brapi.server.core-url")
    private String defaultBrAPICoreUrl;
    @Property(name = "brapi.server.pheno-url")
    private String defaultBrAPIPhenoUrl;
    @Property(name = "brapi.server.geno-url")
    private String defaultBrAPIGenoUrl;
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Property(name = "aws.s3.buckets.geno.bucket")
    private String bucketName;

    @MockBean(ProgramDAO.class)
    ProgramDAO programDAO() {
        return spy(new ProgramDAOImpl(config, dsl, brAPIProvider, brAPIClientProvider, brAPIEndpointProvider, defaultBrAPICoreUrl, defaultBrAPIPhenoUrl, defaultBrAPIGenoUrl, referenceSource, Duration.of(10, ChronoUnit.MINUTES)));
    }

    @MockBean(UserDAO.class)
    UserDAO userDAO() {
        return spy(new UserDAOImpl(config, dsl));
    }

    @MockBean(ImportMappingDAO.class)
    ImportMappingDAO importMappingDAO() {
        return spy(new ImportMappingDAOImpl(config, dsl, objectMapper));
    }

    @MockBean(ImportDAO.class)
    ImportDAO importDAO() {
        return mock(ImportDAO.class);
    }

    @MockBean(BrAPITrialDAO.class)
    BrAPITrialDAO trialDAO() {
        return mock(BrAPITrialDAO.class);
    }

    @MockBean(BrAPIDAOUtil.class)
    BrAPIDAOUtil brAPIDAOUtil() {
        return mock(BrAPIDAOUtil.class);
    }

    @MockBean(SimpleStorageService.class)
    @Named("geno")
    SimpleStorageService simpleStorageService() {
        return spy(new DefaultSimpleStorageService(bucketName, s3Client, presigner));
    }

    @MockBean(BrAPIEndpointProvider.class)
    BrAPIEndpointProvider brAPIEndpointProvider() {
        return spy(new BrAPIEndpointProvider());
    }

    private DockerComposeContainer gigwa;

    public GigwaGenoServiceImplIntegrationTest() {
        gigwa = new DockerComposeContainer(new File("src/test/resources/gigwa-docker.yml"))
                .withExposedService("tomcat", 8080)
                .waitingFor("tomcat",
                            Wait.forHttp("/gigwa")
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
        gigwa.start();
    }

    @BeforeAll
    public void setup() {
        storageService.createBucket();
    }

    @AfterAll
    public void teardown() {
        gigwa.stop();
    }

    @NotNull
    @Override
    public Map<String, String> getProperties() {
        var props = super.getProperties();
        props.put("gigwa.host", "http://"+gigwa.getServiceHost("tomcat", 8080)+":"+gigwa.getServicePort("tomcat", 8080)+"/");
        props.put("gigwa.username", "gigwadmin");
        props.put("gigwa.password", "nimda");

        return props;
    }

    @Test
    public void testUpload() throws IOException, AuthorizationException, ApiException, MimeTypeException {
        UUID programId = UUID.fromString("360766b8-480b-4b0a-862c-7eaa651dda28");
        String programKey = "TEST";
        UUID expId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> uploadGenoData(programId, programKey, expId, importId), "Upload did not complete within the time period");

        assertTrue(storageService.exists(storageService.getDefaultBucketName(), programId + "/" + expId + "/" + importId + ".vcf"), "File was not uploaded to s3");

        BrAPIClient brAPIClient = new BrAPIClient(gigwaHost + "gigwa/rest/brapi/v2");

        ProgramsApi programsApi = new ProgramsApi(brAPIClient);
        try {
            ApiResponse<BrAPIProgramListResponse> brAPIProgramListResponseApiResponse = programsApi.programsGet(ProgramQueryParams.builder()
                                                                                                                                  .programDbId(programKey)
                                                                                                                                  .build());
            assertEquals(1,
                         brAPIProgramListResponseApiResponse.getBody()
                                                            .getResult()
                                                            .getData()
                                                            .size());

            StudiesApi studiesApi = new StudiesApi(brAPIClient);
            ApiResponse<BrAPIStudyListResponse> brAPIStudyListResponseApiResponse = studiesApi.studiesGet(StudyQueryParams.builder()
                                                                                                                          .build());

            assertEquals(1,
                         brAPIStudyListResponseApiResponse.getBody()
                                                          .getResult()
                                                          .getData()
                                                          .stream()
                                                          .filter(brAPIStudy -> brAPIStudy.getStudyName()
                                                                                          .equals(expId.toString()))
                                                          .count());
        } catch (ApiException e) {
            System.err.println(e.getMessage());
            System.err.println(e.getResponseBody());
            throw e;
        }
    }

    @Test
    public void testFetchGermplasmGenotype() throws AuthorizationException, MimeTypeException, IOException, ApiException, DoesNotExistException {
        UUID programId = UUID.fromString("8b667063-480b-4b0a-862c-7eaa651dda28");
        String programKey = "TESTFETCH";
        UUID expId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> uploadGenoData(programId, programKey, expId, importId), "Upload did not complete within the time period");

        BrAPIGermplasm germplasm = new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()).germplasmName("Test Germ");

        BrAPITrial trial = new BrAPITrial().externalReferences(List.of(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                                                                                                   .referenceID(UUID.randomUUID()
                                                                                                                    .toString())));
        doReturn(List.of(trial)).when(trialDAO).getTrials(any(UUID.class));

        doAnswer(invocation -> {
            Object searchObject = invocation.getArgument(2);
            if(searchObject instanceof BrAPIObservationUnitSearchRequest) {
                return List.of(new BrAPIObservationUnit().observationUnitName("USDAMSP1_A01"));
            } else {
                return invocation.callRealMethod();
            }
        }).when(brAPIDAOUtil)
          .search(any(Function.class), any(Function3.class), any());

        doAnswer(InvocationOnMock::callRealMethod).when(brAPIDAOUtil).searchWithToken(any(Function.class), any(Function3.class), any());

        doReturn(new BrAPIClient("", 300000)).when(programDAO).getCoreClient(any(UUID.class));
        doReturn(new BrAPIClient("", 300000)).when(programDAO).getPhenoClient(any(UUID.class));

        GermplasmGenotype germplasmGenotype = gigwaGenoStorageService.retrieveGenotypeData(programId, germplasm);

        assertNotNull(germplasmGenotype);
        assertFalse(germplasmGenotype.getCalls().isEmpty());
        assertFalse(germplasmGenotype.getCallSets().isEmpty());
        assertFalse(germplasmGenotype.getVariants().isEmpty());
    }

    @Test
    public void testSubmitValidFile() throws IOException, AuthorizationException, ApiException, MimeTypeException, DoesNotExistException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITVALID";
        UUID expId = UUID.randomUUID();

        //TODO setup mocks for OUs
        Scanner sc = new Scanner(new FileInputStream("src/test/resources/files/geno/sample.vcf"), "UTF-8");
        String[] headerParts = null;
        boolean foundHeader = false;
        while (sc.hasNextLine() && !foundHeader) {
            String line = sc.nextLine();
            if(line.startsWith("#CHROM")) {
                foundHeader = true;
                headerParts = line.split("\t");
            }
        }
        assertTrue(foundHeader, "Could not find sample.vcf header file");

        List<BrAPIObservationUnit> ous = new ArrayList<>();
        for(int i = 9; i < headerParts.length; i++) {
              ous.add(new BrAPIObservationUnit().observationUnitName(headerParts[i] + " ["+programKey+"-"+(i-7)+"]"));
        }
        setupMocksForSubmitGenoData(expId, ous);

        doAnswer(invocation -> {
            if(invocation.getArgument(2) instanceof BrAPIObservationUnitSearchRequest) {
                return invocation.callRealMethod();
            }
            return invocation.getMock();
        }).when(brAPIDAOUtil).search(any(Function.class), any(Function3.class), any());

        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, expId, "sample.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.ACCEPTED.getCode(), response.getProgress().getStatuscode(), "Error importing geno file: " + response.getProgress().getMessage());
    }

    @Test
    public void testSubmitInvalidHeader() throws IOException, AuthorizationException, ApiException, MimeTypeException, DoesNotExistException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITINVALID";
        UUID expId = UUID.randomUUID();
        setupMocksForSubmitGenoData(expId, Collections.emptyList());
        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, expId, "sample_invalid.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.BAD_REQUEST.getCode(), response.getProgress().getStatuscode());
        assertEquals("Header row is not valid VCF format", response.getProgress().getMessage());
    }

    @Test
    public void testSubmitMissingOUs() throws IOException, AuthorizationException, ApiException, MimeTypeException, DoesNotExistException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITMISSINGOU";
        UUID expId = UUID.randomUUID();

        setupMocksForSubmitGenoData(expId, Collections.emptyList());

        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, expId, "sample.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.BAD_REQUEST.getCode(), response.getProgress().getStatuscode());
        assertEquals("There are samples that do not have an existing observation unit", response.getProgress().getMessage());
    }

    private void setupMocksForSubmitGenoData(UUID expId, List<BrAPIObservationUnit> ous) throws ApiException {
        TrialsApi mockTrialsApi = spy(new TrialsApi());
        doReturn(new ApiResponse<BrAPITrialListResponse>(200,
                                                         new HashMap<>(),
                                                         new BrAPITrialListResponse().result(new BrAPITrialListResponseResult().addDataItem(new BrAPITrial().trialDbId(expId.toString())))))
                .when(mockTrialsApi).trialsGet(any(TrialQueryParams.class));

        doReturn(mockTrialsApi).when(brAPIEndpointProvider).get(any(BrAPIClient.class), eq(TrialsApi.class));

        ObservationUnitsApi mockOUsApi = spy(new ObservationUnitsApi());
        doReturn(new ApiResponse<>(200,
                                   new HashMap<>(),
                                   Pair.of(Optional.of(new BrAPIObservationUnitListResponse().result(new BrAPIObservationUnitListResponseResult().data(ous))),
                                           Optional.empty())))
                .when(mockOUsApi).searchObservationunitsPost(any(BrAPIObservationUnitSearchRequest.class));

        doReturn(mockOUsApi).when(brAPIEndpointProvider).get(any(BrAPIClient.class), eq(ObservationUnitsApi.class));
    }

    private void uploadGenoData(UUID programId, String programKey, UUID expId, UUID importId) throws AuthorizationException, MimeTypeException, IOException, ApiException {
        Program program = Program.builder()
                                 .id(programId)
                                 .key(programKey)
                                 .brapiUrl(BrAPIConstants.SYSTEM_DEFAULT.getValue())
                                 .build();
        doReturn(List.of(program)).when(programDAO)
                                  .get(any(UUID.class));

        User user = User.builder()
                        .id(UUID.randomUUID())
                        .build();
        doReturn(Optional.of(user)).when(userDAO)
                                   .getUser(any(UUID.class));

        ImportMapping mapping = ImportMapping.builder()
                                             .build();
        doReturn(List.of(mapping)).when(importMappingDAO)
                                  .getSystemMappingByName(any(String.class));

        doAnswer(invocation -> {
            String path = (String) invocation.callRealMethod();
            //TODO re-evaluate this...not sure it will work in github actions
            return path.replace("127.0.0.1", "host.docker.internal");
        }).when(storageService).storeMultipartFile(any(String.class), any(PartData.class), any(Consumer.class));

        doAnswer(invocation -> {
            var importEntity = invocation.getArgument(0, ImporterImportEntity.class);
            importEntity.setId(importId);
            return importEntity;
        }).when(importDAO)
          .insert(any(ImporterImportEntity.class));

        ImportProgress progress = ImportProgress.builder()
                                                .createdBy(user.getId())
                                                .createdAt(OffsetDateTime.now())
                                                .updatedAt(OffsetDateTime.now())
                                                .updatedBy(user.getId())
                                                .statuscode((short) HttpStatus.ACCEPTED.getCode())
                                                .message("Uploading file")
                                                .build();
        ImportUpload importUpload = ImportUpload.uploadBuilder()
                                                .createdBy(user.getId())
                                                .createdAt(OffsetDateTime.now())
                                                .updatedBy(user.getId())
                                                .updatedAt(OffsetDateTime.now())
                                                .programId(program.getId())
                                                .importerProgressId(progress.getId())
                                                .importerMappingId(mapping.getId())
                                                .id(importId)
                                                .build();

        System.out.println("======================   program ID: " + program.getId() + " ===============");
        System.out.println("===================   experiment ID: " + expId + " ===============");
        gigwaGenoStorageService.processSubmission(gigwaGenoStorageService.getAuthToken(), program, expId, new TestFileUpload("src/test/resources/files/geno/sample.vcf", MediaType.of("application/vcard")), importUpload, progress);
    }

    private ImportResponse submitGenoData(UUID programId, String programKey, UUID expId, String file) throws AuthorizationException, MimeTypeException, IOException, ApiException, DoesNotExistException {
        Program program = Program.builder()
                                 .id(programId)
                                 .key(programKey)
                                 .brapiUrl(BrAPIConstants.SYSTEM_DEFAULT.getValue())
                                 .build();
        doReturn(List.of(program)).when(programDAO)
                                  .get(any(UUID.class));

        User user = User.builder()
                        .id(UUID.randomUUID())
                        .build();
        doReturn(Optional.of(user)).when(userDAO)
                                   .getUser(any(UUID.class));

        ImportMapping mapping = ImportMapping.builder()
                                             .build();
        doReturn(List.of(mapping)).when(importMappingDAO)
                                  .getSystemMappingByName(any(String.class));

        UUID importId = UUID.randomUUID();
        doAnswer(invocation -> {
            var importEntity = invocation.getArgument(0, ImporterImportEntity.class);
            importEntity.setId(importId);
            return importEntity;
        }).when(importDAO)
          .insert(any(ImporterImportEntity.class));

        doReturn(new BrAPIClient("", 300000)).when(programDAO).getCoreClient(any(UUID.class));
        doReturn(new BrAPIClient("", 300000)).when(programDAO).getPhenoClient(any(UUID.class));

        doAnswer(invocation -> {
            String path = (String) invocation.callRealMethod();
            //TODO re-evaluate this...not sure it will work in github actions
            return path.replace("127.0.0.1", "host.docker.internal");
        }).when(storageService).storeMultipartFile(any(String.class), any(PartData.class), any(Consumer.class));

        System.out.println("======================   program ID: " + program.getId() + " ===============");
        System.out.println("===================   experiment ID: " + expId + " ===============");
        return gigwaGenoStorageService.submitGenoData(user.getId(), programId, expId, new TestFileUpload("src/test/resources/files/geno/"+file, MediaType.of("application/vcard")));
    }

    private class TestFileUpload implements CompletedFileUpload {

        private final String filename;
        private final MediaType mediaType;

        public TestFileUpload(String filename, MediaType mediaType) {
            this.filename = filename;
            this.mediaType = mediaType;

        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(filename);
        }

        @Override
        public byte[] getBytes() throws IOException {
            return getInputStream().readAllBytes();
        }

        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            return ByteBuffer.wrap(getBytes());
        }

        @Override
        public Optional<MediaType> getContentType() {
            return Optional.of(mediaType);
        }

        @Override
        public String getName() {
            return filename;
        }

        @Override
        public String getFilename() {
            int lastIdx = filename.lastIndexOf("/");
            return filename.substring(lastIdx + 1);
        }

        @Override
        public long getSize() {
            try {
                return getBytes().length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getDefinedSize() {
            return getSize();
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    }
}
