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
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.auth.Authentication;
import org.brapi.client.v2.auth.OAuth;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.client.v2.modules.genotype.CallSetsApi;
import org.brapi.client.v2.modules.genotype.CallsApi;
import org.brapi.client.v2.modules.genotype.SamplesApi;
import org.brapi.client.v2.modules.genotype.VariantsApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
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
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenotypeService;
import org.breedinginsight.services.parsers.MimeTypeParser;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
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
public class GigwaGenotypeServiceImpl implements GenotypeService {
    private static final String AUTHORIZATION = "Authorization";
    private static final String X_FORWARDED_SERVER = "X-Forwarded-Server";
    private static final String BEARER = "Bearer ";
    private static final String GIGWA_REST_BASE_PATH = "gigwa/rest/";
    private static final String GIGWA_BRAPI_BASE_PATH = GIGWA_REST_BASE_PATH + BrapiVersion.BRAPI_V2;

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

    private final BrAPIDAOUtil brAPIDAOUtil;

    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public GigwaGenotypeServiceImpl(@Property(name = "gigwa.host") String gigwaHost,
                                    @Property(name = "gigwa.username") String username,
                                    @Property(name = "gigwa.password") String password,
                                    @Property(name = "brapi.server.reference-source") String referenceSource,
                                    ProgramDAO programDAO,
                                    UserDAO userDAO,
                                    ImportDAO importDAO,
                                    ImportMappingDAO importMappingDAO,
                                    @Named("genotype") SimpleStorageService storageService,
                                    S3Client s3Client,
                                    DSLContext dsl,
                                    MimeTypeParser mimeTypeParser,
                                    BrAPIDAOUtil brAPIDAOUtil,
                                    BrAPIEndpointProvider brAPIEndpointProvider) {
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
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    @Override
    public ImportResponse submitGenotypeData(UUID userId, UUID programId, UUID experimentId, CompletedFileUpload uploadedFile) throws DoesNotExistException, AuthorizationException, ApiException {
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
                                                .message("Validating file")
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
        upload.setProgress(progress);

        String gigwaAuthToken;
        try {
            gigwaAuthToken = getAuthToken();
        } catch (AuthorizationException e) {
            progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
            progress.setMessage("An error occurred while trying to connect to genotypic data storage server");
            importDAO.updateProgress(progress);
            throw e;
        }

        try {
            byte[] fileContents = uploadedFile.getBytes();
            if(validateSamples(program, experimentId, fileContents, upload, gigwaAuthToken)) {
                executor.execute(() -> {
                    try {
                        processSubmission(gigwaAuthToken, program, experimentId, fileContents, uploadedFile.getFilename(), upload, progress);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
            }
        } catch (IOException e) {
            progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
            progress.setMessage("An error occurred while trying to validate sample information");
            importDAO.updateProgress(progress);
            throw new RuntimeException(e);
        }

        ImportResponse response = new ImportResponse();
        response.setImportId(upload.getId());
        response.setProgress(progress);
        return response;
    }

    @Override
    public GermplasmGenotype retrieveGenotypeData(UUID programId, BrAPIGermplasm germplasm) throws DoesNotExistException, AuthorizationException, ApiException {
        log.debug("fetching genotypes for " + germplasm.getGermplasmName());
        Program program = getProgram(programId);
        BrAPIClient brAPIClient = programDAO.getCoreClient(programId);
        brAPIClient.setBasePath(gigwaHost + GIGWA_BRAPI_BASE_PATH);
        Authentication authorizationToken = brAPIClient.getAuthentication("AuthorizationToken");
        if(authorizationToken instanceof OAuth) {
            ((OAuth)authorizationToken).setAccessToken(getAuthToken());
        }

        BrAPIClient brapiPhenoClient = programDAO.getPhenoClient(programId);

        if(verifyProgramExists(brAPIClient, program)) {
            List<BrAPIObservationUnit> germplasmOUs = fetchObservationUnits(brapiPhenoClient, germplasm);

            List<BrAPISample> germplasmSamples = fetchSamples(brAPIClient, program, germplasmOUs);

            List<BrAPICallSet> callSets = fetchCallsets(brAPIClient, germplasmSamples);

            List<BrAPICall> calls = fetchCalls(brAPIClient, callSets);

            List<BrAPIVariant> variants = fetchVariants(brAPIClient, calls);

            return GermplasmGenotype.builder()
                                    .germplasm(germplasm)
                                    .calls(calls.stream()
                                                .collect(Collectors.groupingBy(BrAPICall::getCallSetDbId)))
                                    .callSets(callSets.stream()
                                                      .collect(Collectors.toMap(BrAPICallSet::getCallSetDbId, callset -> callset)))
                                    .variants(variants.stream()
                                                      .collect(Collectors.toMap(BrAPIVariant::getVariantDbId, variant -> variant)))
                                    .build();
        } else {
            return new GermplasmGenotype();
        }
    }

    private boolean validateSamples(Program program, UUID experimentId, byte[] fileContents, ImportUpload upload, String gigwaAuthToken) throws DoesNotExistException, ApiException {
        log.debug("Validating samples in submitted VCF file for experiment: " + experimentId);

        BrAPIClient brAPIClient = programDAO.getCoreClient(program.getId());
        brAPIClient.setBasePath(gigwaHost + GIGWA_BRAPI_BASE_PATH);
        Authentication authorizationToken = brAPIClient.getAuthentication("AuthorizationToken");
        if(authorizationToken instanceof OAuth) {
            ((OAuth)authorizationToken).setAccessToken(gigwaAuthToken);
        }
        BrAPIClient brapiPhenoClient = programDAO.getPhenoClient(program.getId());

        Set<String> obsUnitNames = fetchObservationUnits(brapiPhenoClient, experimentId).stream().map(ou -> Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey())).collect(Collectors.toSet());

        log.debug("searching for the VCF header row");
        String[] headerParts = null;
        Scanner sc = new Scanner(new ByteArrayInputStream(fileContents), "UTF-8");
        boolean foundHeader = false;
        while (sc.hasNextLine() && !foundHeader) {
            String line = sc.nextLine();
            if(line.startsWith("#CHROM")) {
                log.debug("Header row found! -> " + line);
                foundHeader = true;
                headerParts = line.split("\t");
            }
        }

        if(!foundHeader) {
            upload.getProgress().setStatuscode((short)HttpStatus.BAD_REQUEST.getCode());
            upload.getProgress().setMessage("Could not find header row in file");
            importDAO.updateProgress(upload.getProgress());
            return false;
        }

        List<String> samples = new ArrayList<>();
        boolean validHeader = false;
        if(headerParts.length >= 8) {
            validHeader = validateVcfHeader(headerParts);
            if(validHeader) {
                int sampleStart = 8;

                if(headerParts[8].equals("FORMAT")) {
                    sampleStart++;
                }

                samples.addAll(Arrays.asList(headerParts)
                                     .subList(sampleStart, headerParts.length));
            }
        }

        if(!validHeader) {
            upload.getProgress().setStatuscode((short)HttpStatus.BAD_REQUEST.getCode());
            upload.getProgress().setMessage("Header row is not valid VCF format");
            importDAO.updateProgress(upload.getProgress());
            return false;
        }

        log.debug("pulled all the samples from the VCF, now checking each one has an OU record");
        List<String> samplesMissingOu = new ArrayList<>();
        samples.forEach(s -> {
            if(!obsUnitNames.contains(s)) {
                samplesMissingOu.add(s);
            }
        });

        if(!samplesMissingOu.isEmpty()) {
            upload.getProgress().setStatuscode((short)HttpStatus.BAD_REQUEST.getCode());
            upload.getProgress().setMessage("There are samples that do not have an existing observation unit");
            importDAO.updateProgress(upload.getProgress());
            return false;
        }

        log.debug("VCF samples are valid!");
        return true;
    }

    private boolean validateVcfHeader(String[] headerParts) {
        if(headerParts.length < 8) {
            return false;
        }

        if(!headerParts[0].equals("#CHROM")) {
            return false;
        }

        if(!headerParts[1].equals("POS")) {
            return false;
        }

        if(!headerParts[2].equals("ID")) {
            return false;
        }

        if(!headerParts[3].equals("REF")) {
            return false;
        }

        if(!headerParts[4].equals("ALT")) {
            return false;
        }

        if(!headerParts[5].equals("QUAL")) {
            return false;
        }

        if(!headerParts[6].equals("FILTER")) {
            return false;
        }

        if(!headerParts[7].equals("INFO")) {
            return false;
        }

        return true;
    }

    private boolean verifyProgramExists(BrAPIClient genoBrAPIClient, Program program) throws ApiException {
        ProgramsApi programsApi = brAPIEndpointProvider.get(genoBrAPIClient, ProgramsApi.class);
        ApiResponse<BrAPIProgramListResponse> brAPIProgramListResponseApiResponse = programsApi.programsGet(new ProgramQueryParams().programDbId(program.getKey()));

        return brAPIProgramListResponseApiResponse.getBody().getResult().getData().size() == 1;
    }

    private List<BrAPISample> fetchSamples(BrAPIClient genoBrAPIClient, Program program, List<BrAPIObservationUnit> observationUnits) throws ApiException {
        log.debug("fetching samples for OUs");
        if(observationUnits.isEmpty()) {
            log.debug("No OUs were supplied, returning");
            return new ArrayList<>();
        }

        SamplesApi samplesApi = brAPIEndpointProvider.get(genoBrAPIClient, SamplesApi.class);

        BrAPISampleSearchRequest sampleSearchRequest = new BrAPISampleSearchRequest();

        sampleSearchRequest.setGermplasmDbIds(observationUnits.stream().map(ou -> program.getKey() + "§" + Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey())).collect(Collectors.toList()));

        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, sampleSearchRequest);
    }

    private List<BrAPIObservationUnit> fetchObservationUnits(BrAPIClient phenoBrAPIClient, BrAPIGermplasm germplasm) throws ApiException {
        ObservationUnitsApi observationUnitsApi = brAPIEndpointProvider.get(phenoBrAPIClient, ObservationUnitsApi.class);

        BrAPIObservationUnitSearchRequest searchRequest = new BrAPIObservationUnitSearchRequest();
        searchRequest.addGermplasmDbIdsItem(germplasm.getGermplasmDbId());

        return brAPIDAOUtil.search(observationUnitsApi::searchObservationunitsPost, observationUnitsApi::searchObservationunitsSearchResultsDbIdGet, searchRequest);
    }

    private List<BrAPIObservationUnit> fetchObservationUnits(BrAPIClient phenoBrAPIClient, UUID experimentId) throws ApiException, DoesNotExistException {
        log.debug("fetching observationUnits for experiment: " + experimentId);
        TrialsApi trialsApi = brAPIEndpointProvider.get(phenoBrAPIClient, TrialsApi.class);
        ApiResponse<BrAPITrialListResponse> brAPITrialListResponseApiResponse = trialsApi.trialsGet(new TrialQueryParams().externalReferenceID(experimentId.toString())
                                                                                                                          .externalReferenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS)));

        BrAPITrial brAPITrial = null;
        if(brAPITrialListResponseApiResponse.getBody().getResult().getData() != null) {
            if (brAPITrialListResponseApiResponse.getBody().getResult().getData().size() == 1) {
                brAPITrial = brAPITrialListResponseApiResponse.getBody().getResult().getData().get(0);
            } else {
                String trialReferenceSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS);
                for (BrAPITrial trial : brAPITrialListResponseApiResponse.getBody().getResult().getData()) {
                    if (trial.getExternalReferences() != null) {
                        Optional<BrAPIExternalReference> xref = trial.getExternalReferences()
                                                                     .stream()
                                                                     .filter(externalReference -> externalReference.getReferenceSource().equals(trialReferenceSource))
                                                                     .findFirst();
                        if (xref.isPresent() && xref.get().getReferenceID().equals(experimentId.toString())) {
                            brAPITrial = trial;
                            break;
                        }
                    }
                }
            }
        }

        if(brAPITrial != null) {
            ObservationUnitsApi observationUnitsApi = brAPIEndpointProvider.get(phenoBrAPIClient, ObservationUnitsApi.class);

            BrAPIObservationUnitSearchRequest searchRequest = new BrAPIObservationUnitSearchRequest();
            searchRequest.addTrialDbIdsItem(brAPITrial.getTrialDbId());

            return brAPIDAOUtil.search(observationUnitsApi::searchObservationunitsPost, observationUnitsApi::searchObservationunitsSearchResultsDbIdGet, searchRequest);
        } else {
            throw new DoesNotExistException("Could not find experiment in database");
        }
    }

    private List<BrAPICallSet> fetchCallsets(BrAPIClient genoBrAPIClient, List<BrAPISample> germplasmSamples) throws ApiException {
        log.debug("fetching callsets for samples");
        if(germplasmSamples.isEmpty()) {
            log.debug("No samples were supplied, returning");
            return new ArrayList<>();
        }

        CallSetsApi callSetsApi = brAPIEndpointProvider.get(genoBrAPIClient, CallSetsApi.class);

        BrAPICallSetsSearchRequest searchRequest = new BrAPICallSetsSearchRequest();
        searchRequest.setGermplasmDbIds(germplasmSamples.stream().map(BrAPISample::getGermplasmDbId).collect(Collectors.toList()));

        return brAPIDAOUtil.search(callSetsApi::searchCallsetsPost, callSetsApi::searchCallsetsSearchResultsDbIdGet, searchRequest);
    }

    private List<BrAPICall> fetchCalls(BrAPIClient genoBrAPIClient, List<BrAPICallSet> callSets) throws ApiException {
        log.debug("fetching calls for callsets");
        if(callSets.isEmpty()) {
            log.debug("No callsets were supplied, returning");
            return new ArrayList<>();
        }
        CallsApi callsApi = brAPIEndpointProvider.get(genoBrAPIClient, CallsApi.class);

        BrAPICallsSearchRequest searchRequest = new BrAPICallsSearchRequest();
        searchRequest.setCallSetDbIds(callSets.stream().map(BrAPICallSet::getCallSetDbId).collect(Collectors.toList()));

        return brAPIDAOUtil.searchWithToken(callsApi::searchCallsPost, callsApi::searchCallsSearchResultsDbIdGet, searchRequest); //breaking bc this uses a pageToken instead of page#
    }

    private List<BrAPIVariant> fetchVariants(BrAPIClient genoBrAPIClient, List<BrAPICall> calls) throws ApiException {
        log.debug("fetching variants for calls");
        if(calls.isEmpty()) {
            log.debug("No calls were supplied, returning");
            return new ArrayList<>();
        }
        List<String> variantIds = calls.stream()
                                       .map(BrAPICall::getVariantDbId)
                                       .distinct()
                                       .collect(Collectors.toList());

        VariantsApi variantsApi = brAPIEndpointProvider.get(genoBrAPIClient, VariantsApi.class);

        BrAPIVariantsSearchRequest searchRequest = new BrAPIVariantsSearchRequest();
        searchRequest.setVariantDbIds(variantIds);

        return brAPIDAOUtil.searchWithToken(variantsApi::searchVariantsPost, variantsApi::searchVariantsSearchResultsDbIdGet, searchRequest);
    }

    protected void processSubmission(String gigwaAuthToken, Program program, UUID experimentId, byte[] fileContents, String filename, ImportUpload upload, ImportProgress progress) throws MimeTypeException, IOException, ApiException {
        Pair<String, Long> uploadedFileResult;
        try {
            progress.setMessage("Uploading file");
            importDAO.updateProgress(progress);
            uploadedFileResult = uploadGenotypeData(program.getId(), experimentId, upload.getId(), fileContents, filename);
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
        String gigwaProgressToken = submitRequestToGigwa(client, program, experimentId, uploadedFileResult.getLeft(), gigwaAuthToken, progress);

        if(checkGigwaProgress(client, gigwaAuthToken, gigwaProgressToken, progress)) {
            log.debug("Gigwa import was successful!");
            progress.setMessage("Import successful");
            progress.setStatuscode((short) HttpStatus.OK.getCode());
            importDAO.updateProgress(progress);
        }
    }

    private boolean checkGigwaProgress(OkHttpClient client, String gigwaAuthToken, String gigwaProgressToken, ImportProgress progress) throws ApiException, IOException {
        String progressToken = gigwaProgressToken.replaceAll("\"", "");
        log.debug("gigwaProgressToken: " + gigwaProgressToken);

        Request progressRequest = new Request.Builder()
                .url(HttpUrl.parse(buildPath("gigwa/progress"))
                            .newBuilder()
                            .addQueryParameter("progressToken", progressToken)
                            .build())
                .header(AUTHORIZATION, BEARER + gigwaAuthToken)
                .build();

        boolean completed = false;
        while (!completed) {
            log.debug("checking gigwa progress");
            try (Response response = client.newCall(progressRequest)
                                           .execute()) {
                if (!response.isSuccessful()) {
                    progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                    progress.setMessage("An error occurred saving the genotypic data");
                    importDAO.updateProgress(progress);
                    throw new ApiException("Gigwa had an error saving the genotype data");
                }
                AtomicReference<String> error = new AtomicReference<>();
                if (response.code() == 200) {
                    String body = Objects.requireNonNull(response.body())
                                         .string();
                    if(body.length() == 0) {
                        error.set("No status response returned, assuming error");
                    } else {
                        log.debug("Progress as of now: " + body);
                        JsonObject gigwaProgress = gson.fromJson(body, JsonObject.class);
                        Optional.ofNullable(gigwaProgress.get("error"))
                                .ifPresent(jsonElement -> error.set(jsonElement.getAsString()));
                        completed = getBooleanValue(gigwaProgress, "complete", false);
                        progress.setMessage(gigwaProgress.get("progressDescription")
                                                         .getAsString());
                        importDAO.updateProgress(progress);
                    }
                } else if(response.code() == 204) {
                    error.set("No status response returned, assuming error");
                }

                var errorVal = error.get();
                if (errorVal != null) {
                    progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                    progress.setMessage("An error occurred saving the genotypic data: " + errorVal);
                    importDAO.updateProgress(progress);
                    throw new ApiException("Gigwa had an error saving the genotype data: " + errorVal);
                } else if (!completed) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }

        return completed;
    }

    /**
     * Submits the upload request to Gigwa, and returns the progress token
     * @param client
     * @param program
     * @param experimentId
     * @param fileUrl
     * @param gigwaAuthToken
     * @param progress
     * @return the progress token to check on the import's progress
     * @throws IOException
     */
    private String submitRequestToGigwa(OkHttpClient client, Program program, UUID experimentId, String fileUrl, String gigwaAuthToken, ImportProgress progress) throws IOException {
        Request request = new Request.Builder()
                .url(HttpUrl.parse(buildPath("gigwa/genotypeImport"))
                            .newBuilder()
                            .addQueryParameter("module", program.getKey())
                            .addQueryParameter("project", experimentId.toString())
                            .addQueryParameter("run", LocalDateTime.now().toString())
                            .addQueryParameter("dataFile1", fileUrl)

//                            .addQueryParameter("ploidy", "4") //TODO CHANGE THIS!!  it's only for the hackathon!!!!

                            .build())
                .header(AUTHORIZATION, BEARER + gigwaAuthToken)
                .header(X_FORWARDED_SERVER, referenceSource)
                .post(RequestBody.create("", MediaType.parse("text/plain")))
                .build();

        log.debug("uploading data to Gigwa");
        try (Response response = client.newCall(request)
                                       .execute()) {
            if (!response.isSuccessful()) {
                progress.setStatuscode((short) HttpStatus.INTERNAL_SERVER_ERROR.getCode());
                progress.setMessage("An error occurred saving the genotypic data");
                importDAO.updateProgress(progress);
                throw new InternalServerException("Unknown error saving genotype data to gigwa");
            } else {
                return response.body().string();
            }
        }
    }

    private Pair<String, Long> uploadGenotypeData(UUID programId, UUID experimentId, UUID uploadId, byte[] fileContents, String filename) throws IOException, MimeTypeException {
        log.debug("saving genotype data to S3");

        if(!storageService.listBucketNames().contains(storageService.getDefaultBucketName())) {
            log.debug("bucket doesn't exist, creating it");
            storageService.createBucket();
        }

        var mimeType = mimeTypeParser.getMimeType(fileContents, filename);

        var key = programId.toString() + "/" + experimentId.toString() + "/" + uploadId + mimeType.getExtension();
        var path = storeMultipartFile(key, fileContents, Map.of("originalFileName", filename));

        Long fileSize = Long.valueOf(fileContents.length);

        return Pair.of(path, fileSize);
    }

    private String storeMultipartFile(String key, byte[] fileContents, Map<String, String> metadata) throws IOException {
        String bucketName = storageService.getDefaultBucketName();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                                                                                                            .bucket(bucketName)
                                                                                                            .key(key)
                                                                                                            .metadata(metadata)
                                                                                                            .build());
        String uploadId = response.uploadId();

        List<CompletedPart> parts = new ArrayList<>();
        int partNumber = 1;
        String etag = s3Client.uploadPart(UploadPartRequest.builder()
                                                            .bucket(bucketName)
                                                            .key(key)
                                                            .uploadId(uploadId)
                                                            .partNumber(partNumber)
                                                            .build(),
                                          software.amazon.awssdk.core.sync.RequestBody.fromBytes(fileContents))
                              .eTag();

        parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());

        log.debug("all parts have been uploaded, completing the upload");
        CompleteMultipartUploadResponse completeMultipartUploadResponse = s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                                                                                                                                         .bucket(bucketName)
                                                                                                                                         .key(key)
                                                                                                                                         .uploadId(uploadId)
                                                                                                                                         .multipartUpload(CompletedMultipartUpload.builder()
                                                                                                                                                                                  .parts(parts)
                                                                                                                                                                                  .build())
                                                                                                                                         .build());
        log.debug("upload complete");
        return completeMultipartUploadResponse.location();
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
