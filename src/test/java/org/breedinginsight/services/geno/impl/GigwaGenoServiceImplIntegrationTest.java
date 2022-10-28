package org.breedinginsight.services.geno.impl;

import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import org.apache.tika.mime.MimeTypeException;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.daos.impl.ImportMappingDAOImpl;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
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
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.invocation.InvocationOnMock;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
    private BrAPIDAOUtil brAPIDAOUtil;

    @Property(name = "gigwa.host")
    private String gigwaHost;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @MockBean(ProgramDAO.class)
    ProgramDAO programDAO() {
        return spy(new ProgramDAOImpl(config, dsl, brAPIProvider, brAPIClientProvider, Duration.of(10, ChronoUnit.MINUTES)));
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

    @Test
    public void testUpload() throws IOException, AuthorizationException, ApiException, MimeTypeException {
        UUID programId = UUID.fromString("360766b8-480b-4b0a-862c-7eaa651dda28");
        String programKey = "TEST";
        UUID expId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        uploadGenoData(programId, programKey, expId, importId);

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
        uploadGenoData(programId, programKey, expId, importId);

        BrAPIGermplasm germplasm = new BrAPIGermplasm().germplasmDbId(UUID.randomUUID().toString());

        BrAPITrial trial = new BrAPITrial().externalReferences(List.of(new BrAPIExternalReference().referenceSource(referenceSource + "/trials")
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
        gigwaGenoStorageService.submit(gigwaGenoStorageService.getAuthToken(), program, expId, new TestFileUpload("src/test/resources/files/geno/sample.vcf", MediaType.of("application/vcard")), importUpload, progress);
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
