/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.auth.Authentication;
import org.brapi.client.v2.auth.OAuth;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.genotype.VendorApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.geno.*;
import org.brapi.v2.model.geno.request.BrAPIVendorOrderSubmissionRequest;
import org.brapi.v2.model.geno.response.BrAPIVendorOrderStatusResponse;
import org.brapi.v2.model.geno.response.BrAPIVendorOrderStatusResponseResult;
import org.brapi.v2.model.geno.response.BrAPIVendorOrderSubmissionSingleResponse;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.BrAPIPlateDAO;
import org.breedinginsight.brapps.importer.daos.BrAPISampleDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.sample.SampleSubmissionImport;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SampleSubmissionDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.FileUtil;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.jooq.JSONB;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class SampleSubmissionService {

    private static final String COLUMN_GENOTYPE = "Genotype";
    private static final String VENDOR_NOT_SUBMITTED_STATUS = "NOT SUBMITTED";
    private static final String VENDOR_SUBMITTED_STATUS = "SUBMITTED";
    private final String referenceSource;
    private String dartBrapiUrl;
    private String dartClientId;
    private String dartToken;
    private Duration requestTimeout;

    private final SampleSubmissionDAO submissionDAO;
    private final BrAPIPlateDAO plateDAO;
    private final BrAPISampleDAO sampleDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramDAO programDAO;
    private final DSLContext dsl;

    @Inject
    public SampleSubmissionService(@Property(name = "brapi.server.reference-source") String referenceSource,
                                   @Property(name = "brapi.vendors.dart.url") String dartBrapiUrl,
                                   @Property(name = "brapi.vendors.dart.client-id") String dartClientId,
                                   @Property(name = "brapi.vendors.dart.token") String dartToken,
                                   @Value(value = "${brapi.read-timeout:5m}") Duration requestTimeout,
                                   SampleSubmissionDAO submissionDAO,
                                   BrAPIPlateDAO plateDAO,
                                   BrAPISampleDAO sampleDAO,
                                   BrAPIEndpointProvider brAPIEndpointProvider,
                                   ProgramDAO programDAO,
                                   DSLContext dsl) {
        this.referenceSource = referenceSource;
        this.dartBrapiUrl = dartBrapiUrl;
        this.dartClientId = dartClientId;
        this.dartToken = dartToken;
        this.requestTimeout = requestTimeout;
        this.submissionDAO = submissionDAO;
        this.plateDAO = plateDAO;
        this.sampleDAO = sampleDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.programDAO = programDAO;
        this.dsl = dsl;
    }

    @Scheduled(fixedDelay = "${brapi.vendor-check-frequency}", initialDelay = "10s")
    void checkSubmissionStatuses() {
        log.trace("checking vendor order statuses");
        List<SampleSubmission> submittedAndNotCompleted = submissionDAO.getSubmittedAndNotComplete();
        log.trace(submittedAndNotCompleted.size() + " orders to check");
        for(var order : submittedAndNotCompleted) {
            try {
                this.checkVendorStatus(order);
            } catch (ApiException e) {
                log.error("Error checking vendor order status: \n\n" + Utilities.generateApiExceptionLogMessage(e), e);
                throw new RuntimeException(e);
            }
        }
        log.trace("vendor order status checks complete, sleeping");
    }

    public SampleSubmission createSubmission(SampleSubmission submission, Program program, ImportUpload upload) throws ApiException {
        submission.setProgramId(program.getId());
        submission.setCreatedByUser(upload.getCreatedByUser());
        submission.setCreatedBy(upload.getCreatedBy());
        submission.setUpdatedByUser(upload.getUpdatedByUser());
        submission.setUpdatedBy(upload.getUpdatedBy());

        submissionDAO.insert(submission);

        List<BrAPIPlate> savedPlates = plateDAO.createPlates(program, submission.getPlates(), upload);
        submission.setPlates(savedPlates);
        Map<String, String> plateNameToDbId = savedPlates.stream().collect(Collectors.toMap(BrAPIPlate::getPlateName, BrAPIPlate::getPlateDbId));

        List<BrAPISample> samplesToSave = submission.getSamples().stream().map(sample -> sample.plateDbId(plateNameToDbId.get(sample.getPlateName()))).collect(Collectors.toList());
        List<BrAPISample> savedSamples = sampleDAO.createSamples(program, samplesToSave, upload);
        submission.setSamples(savedSamples);

        return submission;
    }

    public Optional<SampleSubmission> getSampleSubmission(Program program, UUID submissionId, boolean fetchDetails) throws ApiException {
        if (fetchDetails) {
            return populateSubmissions(program, submissionDAO.getBySubmissionId(program, submissionId)).stream().findFirst();
        } else {
            return submissionDAO.getBySubmissionId(program, submissionId).stream().findFirst();
        }
    }

    public List<SampleSubmission> getProgramSubmissions(Program program) {
        return submissionDAO.getByProgramId(program.getId());
    }

    private List<SampleSubmission> populateSubmissions(Program program, List<SampleSubmission> submissions) throws ApiException {
        List<String> submissionIds = submissions.stream().map(s -> s.getId().toString()).collect(Collectors.toList());
        List<BrAPIPlate> plates = plateDAO.readPlatesBySubmissionIds(program, submissionIds);
        List<BrAPISample> samples = sampleDAO.readSamplesBySubmissionIds(program, submissionIds);

        Map<String, List<BrAPIPlate>> platesBySubmissionId = new HashMap<>();
        String submissionXrefSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS);
        plates.forEach(plate -> {
            BrAPIExternalReference brAPIExternalReference = Utilities.getExternalReference(plate.getExternalReferences(), submissionXrefSource).orElseThrow(() -> new IllegalStateException(String.format("Plate %s does not have a submission ID", plate.getPlateName())));
            List<BrAPIPlate> submissionPlates = platesBySubmissionId.getOrDefault(brAPIExternalReference.getReferenceId(), new ArrayList<>());
            submissionPlates.add(plate);
            platesBySubmissionId.putIfAbsent(brAPIExternalReference.getReferenceId(), submissionPlates);
        });

        Map<String, List<BrAPISample>> samplesBySubmissionId = new HashMap<>();
        samples.forEach(sample -> {
            BrAPIExternalReference brAPIExternalReference = Utilities.getExternalReference(sample.getExternalReferences(), submissionXrefSource).orElseThrow(() -> new IllegalStateException(String.format("Plate %s does not have a submission ID", sample.getPlateName())));
            List<BrAPISample> submissionSamples = samplesBySubmissionId.getOrDefault(brAPIExternalReference.getReferenceId(), new ArrayList<>());
            submissionSamples.add(sample);
            samplesBySubmissionId.putIfAbsent(brAPIExternalReference.getReferenceId(), submissionSamples);
        });

        submissions.forEach(submission -> {
            submission.setPlates(platesBySubmissionId.get(submission.getId().toString()));
            submission.setSamples(samplesBySubmissionId.get(submission.getId().toString()));
        });

        return submissions;
    }

    public Optional<DownloadFile> generateDArTFile(Program program, UUID submissionId) throws ApiException, IOException {
        Optional<SampleSubmission> submission = getSampleSubmission(program, submissionId, true);
        if (submission.isEmpty()) {
            return Optional.empty();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        String filename = Utilities.makePortableFilename(String.format("%s_DArT_Submission_%s.csv", submission.get().getName(), timestamp));

        List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.PLATE_ID).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.ROW).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.COLUMN).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.ORGANISM).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.SPECIES).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(COLUMN_GENOTYPE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.TISSUE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.COMMENTS).dataType(Column.ColumnDataType.STRING).build());

        //TODO sort the samples first
        List<Map<String, Object>> rows = new ArrayList<>();
        submission.get().getSamples().forEach(sample -> {
            Map<String, Object> row = new HashMap<>();
            row.put(SampleSubmissionImport.Columns.PLATE_ID, sample.getPlateName());
            row.put(SampleSubmissionImport.Columns.ROW, sample.getRow());
            row.put(SampleSubmissionImport.Columns.COLUMN, sample.getColumn());
            row.put(SampleSubmissionImport.Columns.ORGANISM, sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_ORGANISM).getAsString());
            row.put(SampleSubmissionImport.Columns.SPECIES, sample.getAdditionalInfo().has(BrAPIAdditionalInfoFields.SAMPLE_SPECIES) ? sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_SPECIES).getAsString() : "");
            row.put(COLUMN_GENOTYPE, sample.getSampleName());
            row.put(SampleSubmissionImport.Columns.TISSUE, sample.getTissueType());
            row.put(SampleSubmissionImport.Columns.COMMENTS, sample.getSampleDescription());

            rows.add(row);
        });


        return Optional.of(new DownloadFile(filename, FileUtil.writeToStreamedFile(columns, rows, FileType.CSV, "Data")));
    }

    public Optional<DownloadFile> generateLookupFile(Program program, UUID submissionId) throws ApiException, IOException {
        Optional<SampleSubmission> submission = getSampleSubmission(program, submissionId, true);
        if (submission.isEmpty()) {
            return Optional.empty();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        String filename = Utilities.makePortableFilename(String.format("%s_Lookup_File_%s.csv", submission.get().getName(), timestamp));

        List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().value(COLUMN_GENOTYPE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.GERMPLASM_NAME).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(SampleSubmissionImport.Columns.GERMPLASM_GID).dataType(Column.ColumnDataType.STRING).build());

        //TODO sort the samples first
        List<Map<String, Object>> rows = new ArrayList<>();
        submission.get().getSamples().forEach(sample -> {
            Map<String, Object> row = new HashMap<>();
            row.put(COLUMN_GENOTYPE, sample.getSampleName());
            row.put(SampleSubmissionImport.Columns.GERMPLASM_NAME, sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_NAME).getAsString());
            row.put(SampleSubmissionImport.Columns.GERMPLASM_GID, sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GID).getAsString());

            rows.add(row);
        });


        return Optional.of(new DownloadFile(filename, FileUtil.writeToStreamedFile(columns, rows, FileType.CSV, "Data")));
    }

    public Optional<BrAPIVendorOrderSubmission> submitOrder(Program program, UUID submissionId, User submittingUser, GenotypeVendor vendor) throws ApiException, IllegalStateException {
        Optional<SampleSubmission> submissionOptional = getSampleSubmission(program, submissionId, true);
        if (submissionOptional.isEmpty()) {
            return Optional.empty();
        }
        SampleSubmission submission = submissionOptional.get();

        //prevent re-submission
        if(Boolean.TRUE.equals(submission.getSubmitted())) {
            if(StringUtils.isNotBlank(submission.getVendorOrderId())) {
                return Optional.of(new BrAPIVendorOrderSubmission().orderId(submission.getVendorOrderId()));
            } else {
                throw new IllegalStateException("Submission has been manually submitted, cannot automatically submit to vendor");
            }
        }

        Map<String, BrAPIVendorPlate> platesForOrder = new HashMap<>();
        submission.getPlates().forEach(plate -> {
            platesForOrder.put(plate.getPlateDbId(), new BrAPIVendorPlate()
                    .clientPlateBarcode(plate.getPlateBarcode())
                    .clientPlateId(plate.getPlateName())
                    .sampleSubmissionFormat(BrAPIPlateFormat.PLATE_96)
            );
        });

        submission.getSamples().forEach(sample -> {
            BrAPIVendorPlate vendorPlate = platesForOrder.get(sample.getPlateDbId());
            vendorPlate.addSamplesItem(new BrAPIVendorSample()
                    .clientSampleId(sample.getSampleName())
                    .clientSampleBarCode(sample.getSampleBarcode())
                    .row(sample.getRow())
                    .column(sample.getColumn())
                    .comments(sample.getSampleDescription())
                    .organismName(sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_ORGANISM).getAsString())
                    .speciesName(sample.getAdditionalInfo().has(BrAPIAdditionalInfoFields.SAMPLE_SPECIES) ? sample.getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_SPECIES).getAsString() : null)
                    .tissueType(sample.getTissueType())
                    .well(String.format("%s%d", sample.getRow(), sample.getColumn()))
            );
        });

        //TODO get info for the specific vendor, and verify program has an account
        BrAPIVendorOrderSubmissionRequest order = new BrAPIVendorOrderSubmissionRequest();
        VendorApi vendorApi;
        if(GenotypeVendor.DART.equals(vendor)) {
            order.setClientId(dartClientId);
            vendorApi = getVendorApi(dartBrapiUrl, dartToken);
        } else {
            throw new IllegalStateException("Unrecognized vendor");
        }

        order.setNumberOfSamples(submission.getSamples().size());
        order.setPlates(new ArrayList<>(platesForOrder.values()));

        ApiResponse<BrAPIVendorOrderSubmissionSingleResponse> response = vendorApi.vendorOrdersPost(order);

        if (response.getBody() == null) {
            throw new ApiException("Response is missing body", response.getStatusCode(), response.getHeaders(), null);
        }
        BrAPIVendorOrderSubmissionSingleResponse body = response.getBody();
        if (body.getResult() == null) {
            throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), response.getBody().toString());
        }
        BrAPIVendorOrderSubmission submittedOrder = body.getResult();

        submission.setVendorOrderId(submittedOrder.getOrderId());
        submission.setVendorStatus(VENDOR_SUBMITTED_STATUS);
        submission.setSubmitted(true);
        submission.setSubmittedDate(OffsetDateTime.now());
        submission.setSubmittedByUser(submittingUser);
        //TODO: TEMPORARY
        submittedOrder.addShipmentFormsItem(new BrAPIShipmentForm().fileDescription("This is a shipment manifest form").fileName("Shipment Manifest").fileURL("https://vendor.org/forms/manifest.pdf"));
        if(submittedOrder.getShipmentForms() != null) {

            submission.setShipmentforms(JSONB.valueOf(vendorApi.getApiClient().getJSON().serialize(submittedOrder.getShipmentForms())));
        }

        dsl.transaction(() -> {
            submissionDAO.update(submission, submittingUser);
        });

        return Optional.of(submittedOrder);
    }

    public Optional<SampleSubmission> checkVendorStatus(Program program, UUID submissionId) throws ApiException {
        Optional<SampleSubmission> submissionOptional = getSampleSubmission(program, submissionId, true);
        if (submissionOptional.isEmpty()) {
            return Optional.empty();
        }
        SampleSubmission submission = submissionOptional.get();

        return Optional.of(checkVendorStatus(submission));
    }

    private SampleSubmission checkVendorStatus(SampleSubmission submission) throws ApiException {
        if(submission.getVendorOrderId() == null || BrAPIVendorOrderStatusResponseResult.StatusEnum.COMPLETED.name().equalsIgnoreCase(submission.getVendorStatus())) {
            return submission;
        }

        VendorApi vendorApi = getVendorApi(dartBrapiUrl, dartToken);
        ApiResponse<BrAPIVendorOrderStatusResponse> response = vendorApi.vendorOrdersOrderIdStatusGet(submission.getVendorOrderId());
        if (response.getBody() == null) {
            throw new ApiException("Response is missing body", response.getStatusCode(), response.getHeaders(), null);
        }
        BrAPIVendorOrderStatusResponse body = response.getBody();
        if (body.getResult() == null) {
            throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), response.getBody().toString());
        }
        BrAPIVendorOrderStatusResponseResult result = body.getResult();

        if(result.getStatus() != null) {
            submission.setVendorStatus(result.getStatus().name());
        }
        submission.setVendorStatusLastCheck(OffsetDateTime.now());

        dsl.transaction(() -> {
            submissionDAO.update(submission, submission.getUpdatedByUser());
        });

        return submission;
    }

    private VendorApi getVendorApi(String url, String authToken) {
        BrAPIClient client = new BrAPIClient(url);
        client.setHttpClient(client.getHttpClient()
                .newBuilder()
                .readTimeout(requestTimeout)
                .build());

        Authentication authorizationToken = client.getAuthentication("AuthorizationToken");
        if(authorizationToken instanceof OAuth) {
            ((OAuth)authorizationToken).setAccessToken(authToken);
        }

        return brAPIEndpointProvider.get(client, VendorApi.class);
    }

    public Optional<SampleSubmission> updateSubmissionStatus(Program program, UUID submissionId, SampleSubmission.Status status, User user) throws ApiException {
        Optional<SampleSubmission> submissionOptional = this.getSampleSubmission(program, submissionId, false);
        if(submissionOptional.isEmpty()) {
            return Optional.empty();
        }

        SampleSubmission submission = submissionOptional.get();
        if(StringUtils.isBlank(submission.getVendorOrderId())) {
            SampleSubmission.Status currentStatus = SampleSubmission.Status.fromValue(submission.getVendorStatus());
            if(currentStatus != status) {
                if((currentStatus == null || currentStatus == SampleSubmission.Status.NOT_SUBMITTED) && status != SampleSubmission.Status.NOT_SUBMITTED) {
                    submission.setSubmitted(true);
                    submission.setSubmittedByUser(user);
                    submission.setSubmittedDate(OffsetDateTime.now());
                }
                submission.setVendorStatus(status.getValue());

                if(status == SampleSubmission.Status.NOT_SUBMITTED){
                    submission.setSubmitted(false);
                    submission.setSubmittedBy(null);
                    submission.setSubmittedByUser(null);
                    submission.setSubmittedDate(null);
                    submission.setVendorStatus(null);
                }

                return Optional.ofNullable(submissionDAO.update(submission, user));
            }
        }

        return submissionOptional;
    }
}
