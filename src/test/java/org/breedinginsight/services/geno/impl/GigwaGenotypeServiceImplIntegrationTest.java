package org.breedinginsight.services.geno.impl;

import com.agorapulse.micronaut.amazon.awssdk.s3.DefaultSimpleStorageService;
import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageService;
import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageServiceConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.auth.Authentication;
import org.brapi.client.v2.auth.OAuth;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.client.v2.modules.genotype.SamplesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.geno.request.BrAPISampleSearchRequest;
import org.brapi.v2.model.geno.response.BrAPISampleListResponse;
import org.brapi.v2.model.geno.response.BrAPISampleListResponseResult;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPISampleDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapi.v2.dao.impl.ImportMappingDAOImpl;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.dao.db.tables.pojos.ImporterImportEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SampleSubmissionDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.daos.impl.ProgramDAOImpl;
import org.breedinginsight.daos.impl.UserDAOImpl;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest(rebuildContext = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GigwaGenotypeServiceImplIntegrationTest extends DatabaseTest {

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
    private GigwaGenotypeServiceImpl gigwaGenoStorageService;

    @Inject
    private UserDAO userDAO;

    @Inject
    private ImportMappingDAO importMappingDAO;

    @Inject
    private ImportDAO importDAO;

    @Inject
    private SampleSubmissionDAO sampleSubmissionDAO;

    @Inject
    private BrAPISampleDAO sampleDAO;

    @Inject
    private BrAPIGermplasmDAO germplasmDAO;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    @Named("genotype")
    private SimpleStorageService storageService;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private S3Client s3Client;

    @Inject
    private S3Presigner presigner;

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

    @Property(name = "aws.s3.buckets.genotype.bucket")
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

    @MockBean(BrAPIEndpointProvider.class)
    BrAPIEndpointProvider brAPIEndpointProvider() {
        return spy(new BrAPIEndpointProvider());
    }

    @MockBean(BrAPISampleDAO.class)
    BrAPISampleDAO sampleDAO() {
        return mock(BrAPISampleDAO.class);
    }

    @MockBean(SimpleStorageService.class)
    @Named("genotype")
    SimpleStorageService simpleStorageService() {
        return spy(new DefaultSimpleStorageService(bucketName, s3Client, presigner));
    }

    // @MockBean cannot replace SampleSubmissionDAO here because it extends generated jOOQ DAO code;
    // Scope this replacement to this spec so it does not affect other test contexts.
    @Factory
    @Requires(property = "micronaut.test.active.spec", value = "org.breedinginsight.services.geno.impl.GigwaGenotypeServiceImplIntegrationTest")
    static class SampleSubmissionTestFactory {

        @Singleton
        @Replaces(SampleSubmissionDAO.class)
        SampleSubmissionDAO sampleSubmissionDAO() {
            return mock(SampleSubmissionDAO.class);
        }
    }

    @Factory
    @Requires(property = "micronaut.test.active.spec", value = "org.breedinginsight.services.geno.impl.GigwaGenotypeServiceImplIntegrationTest")
    static class GermplasmDaoTestFactory {

        @Singleton
        @Replaces(BrAPIGermplasmDAO.class)
        BrAPIGermplasmDAO germplasmDAO() {
            return mock(BrAPIGermplasmDAO.class);
        }
    }


    private GenericContainer gigwa;

    private GenericContainer mongo;

    private LocalStackContainer localStackContainer;

    public GigwaGenotypeServiceImplIntegrationTest() {
        super();
        mongo = new GenericContainer<>("mongo:4.2.24")
                .withNetwork(super.getNetwork())
                .withNetworkAliases("gigwa_db")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(27017)
                .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo")
                .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo")
                .withCommand("--profile 0 --slowms 60000 --storageEngine wiredTiger --wiredTigerCollectionBlockCompressor=zstd --directoryperdb --quiet");
        mongo.start();

        String gigwaAllowedServer = StringUtils.isNotBlank(System.getenv("BRAPI_REFERENCE_SOURCE")) ? System.getenv("BRAPI_REFERENCE_SOURCE") : "breedinginsight.org";

        gigwa = new GenericContainer<>("breedinginsight/gigwa:develop")
                .withNetwork(super.getNetwork())
                .withNetworkAliases("gigwa")
                .withImagePullPolicy(PullPolicy.alwaysPull())
                .withExposedPorts(8080)
                .withEnv("MONGO_IP", "gigwa_db")
                .withEnv("MONGO_PORT", "27017")
                .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo")
                .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo")
                .withEnv("GIGWA.serversAllowedToImport", gigwaAllowedServer)
                .waitingFor(
                        Wait.forHttp("/gigwa")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
        gigwa.start();

        localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack")
                                                                     .withTag("3.0.2"))
                .withServices(LocalStackContainer.Service.S3)
                .withNetwork(super.getNetwork())
                .withNetworkAliases("localstack")
                .withEnv("LOCALSTACK_HOST", "localstack");
        localStackContainer.start();
    }

    @NotNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = super.getProperties();

        properties.put("gigwa.host", "http://"+gigwa.getContainerIpAddress()+":"+gigwa.getMappedPort(8080)+"/");
        properties.put("gigwa.username", "gigwadmin");
        properties.put("gigwa.password", "nimda");

        properties.put("aws.region", localStackContainer.getRegion());
        properties.put("aws.accessKeyId", localStackContainer.getAccessKey());
        properties.put("aws.secretKey", localStackContainer.getSecretKey());
        properties.put("aws.s3.buckets.genotype.bucket", "test");
        properties.put("aws.s3.endpoint", String.valueOf(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3)));

        return properties;
    }

    @BeforeAll
    public void setup() throws IllegalAccessException, NoSuchFieldException {
        applicationContext.registerSingleton((BeanCreatedEventListener<SimpleStorageServiceConfiguration>) event -> {
            SimpleStorageServiceConfiguration conf = event.getBean();
            if (conf.getEndpoint() != null) {
                return conf;
            }
            conf.setEndpoint(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString());
            conf.setRegion(localStackContainer.getRegion());
            conf.setBucket("test");
            return conf;
        }, false);

        storageService = applicationContext.getBean(SimpleStorageService.class, Qualifiers.byName("genotype"));
        storageService.createBucket();
     }

    @AfterAll
    public void teardown() {
        gigwa.stop();
        mongo.stop();
        localStackContainer.stop();
    }

    @Test
    public void testUpload() throws ApiException, AuthorizationException {
        UUID programId = UUID.fromString("360766b8-480b-4b0a-862c-7eaa651dda28");
        String programKey = "TEST";
        UUID submissionId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> uploadGenoData(programId, programKey, submissionId, importId), "Upload did not complete within the time period");

        assertTrue(storageService.exists(storageService.getDefaultBucketName(), programId + "/" + submissionId + "/" + importId + ".vcf"), "File was not uploaded to s3");

        BrAPIClient brAPIClient = new BrAPIClient(gigwaHost + "gigwa/rest/brapi/v2");
        Authentication authorizationToken = brAPIClient.getAuthentication("AuthorizationToken");
        if(authorizationToken instanceof OAuth) {
            ((OAuth)authorizationToken).setAccessToken(gigwaGenoStorageService.getAuthToken());
        }

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
                                                                                          .equals(submissionId.toString()))
                                                          .count());
        } catch (ApiException e) {
            System.err.println(e.getMessage());
            System.err.println(e.getResponseBody());
            throw e;
        }
    }

    //TODO: Enable in BI-2841
    @Test
    //@Disabled("BI-2841: retrieveGenotypeData should target BrAPI samples directly instead of resolving samples through observation units")
    public void testFetchGermplasmGenotype() throws AuthorizationException, ApiException, DoesNotExistException {
        UUID programId = UUID.fromString("8b667063-480b-4b0a-862c-7eaa651dda28");
        String programKey = "TESTFETCH";
        UUID submissionId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> uploadGenoData(programId, programKey, submissionId, importId), "Upload did not complete within the time period");

        BrAPIGermplasm germplasm = new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString()).germplasmName("Test Germ");

        SamplesApi mockSamplesApi = spy(new SamplesApi());
        BrAPISample sample = new BrAPISample().sampleName("USDAMSP1_A01")
                                             .germplasmDbId(germplasm.getGermplasmDbId());
        doReturn(new ApiResponse<>(200,
                                   new HashMap<>(),
                                   Pair.of(Optional.of(new BrAPISampleListResponse().result(new BrAPISampleListResponseResult().data(List.of(sample)))),
                                           Optional.empty())))
                .when(mockSamplesApi).searchSamplesPost(any(BrAPISampleSearchRequest.class));

        doReturn(mockSamplesApi).when(brAPIEndpointProvider).get(any(BrAPIClient.class), eq(SamplesApi.class));

        doReturn(new BrAPIClient("", 300000)).when(programDAO).getCoreClient(any(UUID.class));
        doReturn(new BrAPIClient("", 300000)).when(programDAO).getPhenoClient(any(UUID.class));

        assertTrue(mockingDetails(germplasmDAO).isMock(), germplasmDAO.getClass().getName());

        doReturn(germplasm).when(germplasmDAO)
                .getGermplasmByUUID(any(String.class), any(UUID.class));

        GermplasmGenotype germplasmGenotype = gigwaGenoStorageService.retrieveGenotypeData(programId, UUID.randomUUID());

        verify(brAPIEndpointProvider, never()).get(any(BrAPIClient.class), eq(ObservationUnitsApi.class));
        verify(mockSamplesApi).searchSamplesPost(argThat(searchRequest -> searchRequest.getGermplasmDbIds() != null &&
                                                                          searchRequest.getGermplasmDbIds().contains(germplasm.getGermplasmDbId()) &&
                                                                          (searchRequest.getObservationUnitDbIds() == null || searchRequest.getObservationUnitDbIds().isEmpty())));
        assertNotNull(germplasmGenotype);
        assertFalse(germplasmGenotype.getCalls().isEmpty());
        assertFalse(germplasmGenotype.getCallSets().isEmpty());
        assertFalse(germplasmGenotype.getVariants().isEmpty());
    }

    @Test
    public void testSubmitValidFile() throws IOException, ApiException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITVALID";
        UUID submissionId = UUID.randomUUID();

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

        List<BrAPISample> samples = new ArrayList<>();
        for(int i = 9; i < headerParts.length; i++) {
              samples.add(new BrAPISample().sampleName(headerParts[i]));
        }
        setupMocksForSubmitGenoData(programId, submissionId, samples);

        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, submissionId, "sample.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.ACCEPTED.getCode(), response.getProgress().getStatuscode(), "Error importing geno file: " + response.getProgress().getMessage());
    }

    @Test
    public void testSubmitInvalidHeader() throws ApiException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITINVALID";
        UUID submissionId = UUID.randomUUID();
        setupMocksForSubmitGenoData(programId, submissionId, Collections.emptyList());
        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, submissionId, "sample_invalid.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.BAD_REQUEST.getCode(), response.getProgress().getStatuscode());
        assertEquals("Header row is not valid VCF format", response.getProgress().getMessage());
    }

    @Test
    public void testSubmitMissingSubmissionSamples() throws ApiException {
        UUID programId = UUID.fromString("29162e85-e739-4f19-9fd0-0c377ed59956");
        String programKey = "TESTSUBMITMISSINGOU";
        UUID submissionId = UUID.randomUUID();

        setupMocksForSubmitGenoData(programId, submissionId, Collections.emptyList());

        AtomicReference<ImportResponse> importResponse = new AtomicReference<>();
        assertTimeout(Duration.of(2, ChronoUnit.MINUTES), () -> importResponse.set(submitGenoData(programId, programKey, submissionId, "sample.vcf")), "Upload did not complete within the time period");

        ImportResponse response = importResponse.get();
        assertNotNull(response);
        assertNotNull(response.getProgress());
        assertEquals((short)HttpStatus.BAD_REQUEST.getCode(), response.getProgress().getStatuscode());
        assertEquals("There are samples that are not linked to the selected submission", response.getProgress().getMessage());
    }

    private void setupMocksForSubmitGenoData(UUID programId, UUID submissionId, List<BrAPISample> samples) throws ApiException {
        SampleSubmission submission = new SampleSubmission();
        submission.setId(submissionId);
        submission.setProgramId(programId);

        doReturn(List.of(submission)).when(sampleSubmissionDAO)
                                     .getBySubmissionId(any(Program.class), eq(submissionId));
        doReturn(samples).when(sampleDAO)
                         .readSamplesBySubmissionIds(any(Program.class), eq(List.of(submissionId.toString())));
    }

    private void uploadGenoData(UUID programId, String programKey, UUID submissionId, UUID importId) throws AuthorizationException, MimeTypeException, IOException, ApiException {
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
        System.out.println("===================   submission ID: " + submissionId + " ===============");
        gigwaGenoStorageService.processSubmission(gigwaGenoStorageService.getAuthToken(), program, submissionId, new TestFileUpload("src/test/resources/files/geno/sample.vcf", MediaType.of("application/vcard")).getBytes(), "sample.vcf", importUpload, progress);
    }

    private ImportResponse submitGenoData(UUID programId, String programKey, UUID submissionId, String file) throws AuthorizationException, IOException, ApiException, DoesNotExistException {
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

        System.out.println("======================   program ID: " + program.getId() + " ===============");
        System.out.println("===================   submission ID: " + submissionId + " ===============");
        return gigwaGenoStorageService.submitGenotypeData(user.getId(), programId, submissionId, new TestFileUpload("src/test/resources/files/geno/"+file, MediaType.of("application/vcard")));
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
