package org.breedinginsight.services.geno.impl;

import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.auth.Authentication;
import org.brapi.client.v2.auth.OAuth;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.genotype.CallSetsApi;
import org.brapi.client.v2.modules.genotype.CallsApi;
import org.brapi.client.v2.modules.genotype.SamplesApi;
import org.brapi.client.v2.modules.genotype.VariantsApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.geno.BrAPICall;
import org.brapi.v2.model.geno.BrAPICallSet;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.geno.BrAPIVariant;
import org.brapi.v2.model.geno.request.BrAPICallSetsSearchRequest;
import org.brapi.v2.model.geno.request.BrAPICallsSearchRequest;
import org.brapi.v2.model.geno.request.BrAPISampleSearchRequest;
import org.brapi.v2.model.geno.request.BrAPIVariantsSearchRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenoService;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectAttributesResponse;
import software.amazon.awssdk.services.s3.model.ObjectAttributes;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class GigwaGenoServiceImpl implements GenoService {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String GIGWA_REST_BASE_PATH = "gigwa/rest/";
    private static final String GIGWA_BRAPI_BASE_PATH = GIGWA_REST_BASE_PATH + "brapi/v2";

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");

    private final Executor executor = Executors.newCachedThreadPool();

    private String referenceSource;
    private final String gigwaHost;
    private final String username;
    private final String password;
    private final Gson gson;

    private final ProgramDAO programDAO;
    private final UserDAO userDAO;
    private final ImportDAO importDAO;

    private final ImportMappingDAO importMappingDAO;
    private final SimpleStorageService storageService;

    private final S3Client s3Client;

    private final DSLContext dsl;

    private final MimeTypeParser mimeTypeParser;

    private final BrAPITrialDAO trialDAO;

    private final BrAPIDAOUtil brAPIDAOUtil;

    @Inject
    public GigwaGenoServiceImpl(@Property(name = "gigwa.host") String gigwaHost,
                                @Property(name = "gigwa.username") String username,
                                @Property(name = "gigwa.password") String password,
                                @Property(name = "brapi.server.reference-source") String referenceSource,
                                ProgramDAO programDAO,
                                UserDAO userDAO,
                                ImportDAO importDAO,
                                ImportMappingDAO importMappingDAO,
                                @Named("geno") SimpleStorageService storageService,
                                S3Client s3Client,
                                DSLContext dsl,
                                MimeTypeParser mimeTypeParser,
                                BrAPITrialDAO trialDAO,
                                BrAPIDAOUtil brAPIDAOUtil) {
        this.gigwaHost = gigwaHost.endsWith("/") ? gigwaHost : gigwaHost + "/";
        this.username = username;
        this.password = password;
        this.referenceSource = referenceSource;
        this.gson = new GsonBuilder().create();
        this.programDAO = programDAO;
        this.userDAO = userDAO;
        this.importDAO = importDAO;
        this.importMappingDAO = importMappingDAO;
        this.storageService = storageService;
        this.s3Client = s3Client;
        this.dsl = dsl;
        this.mimeTypeParser = mimeTypeParser;
        this.trialDAO = trialDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
    }

    @Override
    public ImportResponse submitGenoData(UUID userId, UUID programId, UUID experimentId, CompletedFileUpload uploadedFile) throws DoesNotExistException, AuthorizationException {
        Program program = getProgram(programId);

        User user = userDAO.getUser(userId)
                           .orElseThrow(() -> new DoesNotExistException("User ID does not exist"));

        ImportMapping mapping = importMappingDAO.getSystemMappingByName("GenotypicDataImport")
                                                .get(0);

        ImportUpload upload;
        ImportProgress progress = ImportProgress.builder()
                                                .createdBy(user.getId())
                                                .createdAt(OffsetDateTime.now())
                                                .updatedAt(OffsetDateTime.now())
                                                .updatedBy(userId)
                                                .statuscode((short) HttpStatus.ACCEPTED.getCode())
                                                .message("Uploading file")
                                                .build();
        try {
            upload = dsl.transactionResult(configuration -> {
                importDAO.createProgress(progress);
                ImportUpload importUpload = ImportUpload.uploadBuilder()
                                                        .createdBy(user.getId())
                                                        .createdAt(OffsetDateTime.now())
                                                        .updatedBy(user.getId())
                                                        .updatedAt(OffsetDateTime.now())
                                                        .programId(programId)
                                                        .importerProgressId(progress.getId())
                                                        .importerMappingId(mapping.getId())
                                                        .userId(user.getId())
                                                        .uploadFileName(uploadedFile.getFilename())
                                                        .build();

                importDAO.insert(importUpload);

                return importUpload;
            });
        } catch (Exception e) {
            log.error("Exception setting up import progress", e);
            throw e;
        }

        progress.setId(upload.getImporterProgressId());

        //TODO fetch the experiment's observation units
        //TODO fetch the samples in the VCF file
        //TODO validate that each sample in the file exists as an OU (by name) in the experiment
        //TODO fetch geno data for a germplasm record

        String gigwaAuthToken;
        try {
            gigwaAuthToken = getAuthToken();
        } catch (AuthorizationException e) {
            progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
            progress.setMessage("An error occurred while trying to connect to genotypic data storage server");
            importDAO.updateProgress(progress);
            throw e;
        }

        executor.execute(() -> {
            try {
                submit(gigwaAuthToken, program, experimentId, uploadedFile, upload, progress);
            } catch (Exception ignored) {
            }
        });

        ImportResponse response = new ImportResponse();
        response.setImportId(upload.getId());
        return response;
    }

    @Override
    public GermplasmGenotype retrieveGenotypeData(UUID programId, BrAPIGermplasm germplasm) throws DoesNotExistException, AuthorizationException {
        log.debug("fetching genotypes for " + germplasm.getGermplasmName());
        Program program = getProgram(programId);
        BrAPIClient brAPIClient = programDAO.getCoreClient(programId);
        brAPIClient.setBasePath(gigwaHost + GIGWA_BRAPI_BASE_PATH);
        Authentication authorizationToken = brAPIClient.getAuthentication("AuthorizationToken");
        if(authorizationToken instanceof OAuth) {
            ((OAuth)authorizationToken).setAccessToken(getAuthToken());
        }

        BrAPIClient brapiPhenoClient = programDAO.getPhenoClient(programId);

        try {
            List<BrAPIObservationUnit> germplasmOUs = fetchObservationUnits(brapiPhenoClient, germplasm);

            List<BrAPISample> germplasmSamples = fetchSamples(brAPIClient, program, germplasmOUs);

            List<BrAPICallSet> callSets = fetchCallsets(brAPIClient, germplasmSamples);

            List<BrAPICall> calls = fetchCalls(brAPIClient, callSets);

            List<BrAPIVariant> variants = fetchVariants(brAPIClient, calls);

            return GermplasmGenotype.builder()
                                    .germplasm(germplasm)
                                    .calls(calls.stream().collect(Collectors.groupingBy(BrAPICall::getCallSetDbId)))
                                    .callSets(callSets.stream().collect(Collectors.toMap(BrAPICallSet::getCallSetDbId, callset -> callset)))
                                    .variants(variants.stream().collect(Collectors.toMap(BrAPIVariant::getVariantDbId, variant -> variant)))
                                    .build();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    private List<BrAPISample> fetchSamples(BrAPIClient brAPIClient, Program program, List<BrAPIObservationUnit> observationUnits) throws ApiException {
        log.debug("fetching samples for OUs");
        if(observationUnits.isEmpty()) {
            log.debug("No OUs were supplied, returning");
            return new ArrayList<>();
        }

        SamplesApi samplesApi = new SamplesApi(brAPIClient);

        BrAPISampleSearchRequest sampleSearchRequest = new BrAPISampleSearchRequest();

        sampleSearchRequest.setGermplasmDbIds(observationUnits.stream().map(ou -> program.getKey() + "§" + Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey())).collect(Collectors.toList()));

        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, sampleSearchRequest);
    }

    private List<BrAPIObservationUnit> fetchObservationUnits(BrAPIClient brAPIClient, BrAPIGermplasm germplasm) throws ApiException {
        ObservationUnitsApi observationUnitsApi = new ObservationUnitsApi(brAPIClient);

        BrAPIObservationUnitSearchRequest searchRequest = new BrAPIObservationUnitSearchRequest();
        searchRequest.addGermplasmDbIdsItem(germplasm.getGermplasmDbId());

        return brAPIDAOUtil.search(observationUnitsApi::searchObservationunitsPost, observationUnitsApi::searchObservationunitsSearchResultsDbIdGet, searchRequest);
    }

    private List<BrAPICallSet> fetchCallsets(BrAPIClient brAPIClient, List<BrAPISample> germplasmSamples) throws ApiException {
        log.debug("fetching callsets for samples");
        if(germplasmSamples.isEmpty()) {
            log.debug("No samples were supplied, returning");
            return new ArrayList<>();
        }

        CallSetsApi callSetsApi = new CallSetsApi(brAPIClient);

        BrAPICallSetsSearchRequest searchRequest = new BrAPICallSetsSearchRequest();
        searchRequest.setGermplasmDbIds(germplasmSamples.stream().map(BrAPISample::getGermplasmDbId).collect(Collectors.toList()));

        return brAPIDAOUtil.search(callSetsApi::searchCallsetsPost, callSetsApi::searchCallsetsSearchResultsDbIdGet, searchRequest);
    }

    private List<BrAPICall> fetchCalls(BrAPIClient brAPIClient, List<BrAPICallSet> callSets) throws ApiException {
        log.debug("fetching calls for callsets");
        if(callSets.isEmpty()) {
            log.debug("No callsets were supplied, returning");
            return new ArrayList<>();
        }
        CallsApi callsApi = new CallsApi(brAPIClient);

        BrAPICallsSearchRequest searchRequest = new BrAPICallsSearchRequest();
        searchRequest.setCallSetDbIds(callSets.stream().map(BrAPICallSet::getCallSetDbId).collect(Collectors.toList()));

        return brAPIDAOUtil.searchWithToken(callsApi::searchCallsPost, callsApi::searchCallsSearchResultsDbIdGet, searchRequest); //breaking bc this uses a pageToken instead of page#
    }

    private List<BrAPIVariant> fetchVariants(BrAPIClient brAPIClient, List<BrAPICall> calls) throws ApiException {
        log.debug("fetching variants for calls");
        if(calls.isEmpty()) {
            log.debug("No calls were supplied, returning");
            return new ArrayList<>();
        }
        List<String> variantIds = calls.stream()
                                       .map(BrAPICall::getVariantDbId)
                                       .distinct()
                                       .collect(Collectors.toList());

        VariantsApi variantsApi = new VariantsApi(brAPIClient);

        BrAPIVariantsSearchRequest searchRequest = new BrAPIVariantsSearchRequest();
        searchRequest.setVariantDbIds(variantIds);

        return brAPIDAOUtil.searchWithToken(variantsApi::searchVariantsPost, variantsApi::searchVariantsSearchResultsDbIdGet, searchRequest);
    }

    protected void submit(String gigwaAuthToken, Program program, UUID experimentId, CompletedFileUpload uploadedFile, ImportUpload upload, ImportProgress progress) throws MimeTypeException, IOException, ApiException {
        Pair<String, Long> uploadedFileResult;
        try {
            uploadedFileResult = uploadGenoData(program.getId(), experimentId, upload.getId(), uploadedFile);
            log.debug("file saved to: " + uploadedFileResult.getLeft());
        } catch (Exception e) {
            progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
            progress.setMessage("An error occurred uploading the genotypic data");
            importDAO.updateProgress(progress);
            throw e;
        }

        progress.setMessage("Importing file");
        importDAO.updateProgress(progress);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(HttpUrl.parse(buildPath("gigwa/genotypeImport"))
                            .newBuilder()
                            .addQueryParameter("module", program.getKey())
                            .addQueryParameter("project", experimentId.toString())
                            .addQueryParameter("run",
                                               LocalDateTime.now()
                                                            .toString())
                            .addQueryParameter("dataFile1", uploadedFileResult.getLeft())

                             .addQueryParameter("ploidy", "4") //TODO CHANGE THIS!!  it's only for the hackathon!!!!

                            .build())
                .header(AUTHORIZATION, BEARER + gigwaAuthToken)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .build();

        log.debug("uploading data to Gigwa");
        try (Response response = client.newCall(request)
                                       .execute()) {
            if (!response.isSuccessful()) {
                progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                progress.setMessage("An error occurred saving the genotypic data");
                importDAO.updateProgress(progress);
                throw new InternalServerException("Unknown error saving geno data to gigwa");
            }
        }

        Request progressRequest = new Request.Builder()
                .url(buildPath("gigwa/progress"))
                .header(AUTHORIZATION, BEARER + gigwaAuthToken)
                .build();

        boolean completed = false;
        var timeout = uploadedFileResult.getRight() / 1000 / 3;
        while (!completed) {
            log.debug("checking gigwa progress");
            try (Response response = client.newCall(progressRequest)
                                           .execute()) {
                if (!response.isSuccessful()) {
                    progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                    progress.setMessage("An error occurred saving the genotypic data");
                    importDAO.updateProgress(progress);
                    throw new ApiException("Gigwa had an error saving the geno data");
                }
                AtomicReference<String> error = new AtomicReference<>();
                if (response.code() == 200) {
                    String body = Objects.requireNonNull(response.body())
                                         .string();
                    log.debug("Progress as of now: " + body);
                    JsonObject gigwaProgress = gson.fromJson(body, JsonObject.class);
                    Optional.ofNullable(gigwaProgress.get("error")).ifPresent(jsonElement -> error.set(jsonElement.getAsString()));
                    completed = getBooleanValue(gigwaProgress, "complete", false);
                    progress.setMessage(gigwaProgress.get("progressDescription")
                                                     .getAsString());
                    importDAO.updateProgress(progress);
                }

                var errorVal = error.get();
                if (errorVal != null) {
                    progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                    progress.setMessage("An error occurred saving the genotypic data: " + errorVal);
                    importDAO.updateProgress(progress);
                    throw new ApiException("Gigwa had an error saving the geno data: " + errorVal);
                } else if (!completed) {
                    try {
                        timeout--;
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        log.debug("Gigwa import was successful!");
        progress.setMessage("Import successful");
        progress.setStatuscode((short) HttpStatus.OK.getCode());
        importDAO.updateProgress(progress);
    }

    private Pair<String, Long> uploadGenoData(UUID programId, UUID experimentId, UUID uploadId, CompletedFileUpload uploadedFile) throws IOException, MimeTypeException {
        log.debug("saving geno data to S3");

        var mimeType = mimeTypeParser.getMimeType(uploadedFile);

        var key = programId.toString() + "/" + experimentId.toString() + "/" + uploadId + mimeType.getExtension();
        var path = storageService.storeMultipartFile(key,
                                                     uploadedFile,
                                                     builder -> builder.metadata(Map.of("originalFileName", uploadedFile.getFilename()))
                                                                       .build());

        GetObjectAttributesResponse objectAttributes = s3Client.getObjectAttributes(builder -> builder.bucket(storageService.getDefaultBucketName())
                                                                                                      .key(key)
                                                                                                      .objectAttributes(ObjectAttributes.OBJECT_SIZE)
                                                                                                      .build());

        Long fileSize = objectAttributes.objectSize();

        return Pair.of(path, fileSize);
    }

    private boolean getBooleanValue(JsonObject progress, String key, boolean defaultVal) {
        if (progress.has(key)) {
            return progress.get(key)
                           .getAsBoolean();
        }
        return defaultVal;
    }

    protected final String getAuthToken() throws AuthorizationException {
        OkHttpClient authClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(buildPath("gigwa/generateToken"))
                .post(RequestBody.create(gson.toJson(Map.of("username", username, "password", password)), MEDIA_TYPE_JSON))
                .build();
        try (Response response = authClient.newCall(request)
                                           .execute()) {
            if (!response.isSuccessful()) {
                throw new AuthorizationException("Unable to authorize with Gigwa server");
            } else {
                JsonObject responseBody = gson.fromJson(response.body()
                                                                .string(), JsonObject.class);
                if (responseBody.has("token")) {
                    return responseBody.get("token")
                                       .getAsString();
                } else {
                    throw new AuthorizationException("Authorization token was not returned");
                }
            }
        } catch (IOException e) {
            throw new AuthorizationException("Error authorizing with Gigwa server", e);
        }
    }

    private String buildPath(String requestPath) {
        return gigwaHost + GIGWA_REST_BASE_PATH + requestPath;
    }

    private Program getProgram(UUID programId) throws DoesNotExistException {
        return programDAO.get(programId)
                         .stream()
                         .findFirst()
                         .orElseThrow(() -> new DoesNotExistException("Program ID does not exist"));
    }
}
