package org.breedinginsight.services.geno.impl;

import com.agorapulse.micronaut.amazon.awssdk.s3.SimpleStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenotypeMultipartUploadTest {
    @TempDir
    Path directory;

    @Test
    void failedPartOrCompletionAbortsUploadAndPreservesOriginalError() throws Exception {
        for (boolean failPart : new boolean[]{true, false}) {
            S3Client s3 = mock(S3Client.class);
            SimpleStorageService storage = mock(SimpleStorageService.class);
            when(storage.getDefaultBucketName()).thenReturn("genotype");
            when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                    .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-id").build());
            RuntimeException failure = new RuntimeException("Storage failed");
            if (failPart) {
                when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenThrow(failure);
            } else {
                when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                        .thenReturn(UploadPartResponse.builder().eTag("etag").build());
                when(s3.completeMultipartUpload(any(CompleteMultipartUploadRequest.class))).thenThrow(failure);
            }
            RuntimeException abortFailure = new RuntimeException("Abort failed");
            when(s3.abortMultipartUpload(any(AbortMultipartUploadRequest.class))).thenThrow(abortFailure);
            GigwaGenotypeServiceImpl service = new GigwaGenotypeServiceImpl(
                    "http://localhost/", "", "", "", null, null, null, null, null, null, null,
                    storage, s3, null, null, null, null, null);
            Path file = Files.writeString(directory.resolve("sample.vcf"), "sample VCF");
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> service.storeMultipartFile("key", file, Collections.emptyMap())));
            assertArrayEquals(new Throwable[]{abortFailure}, failure.getSuppressed());
            verify(s3).abortMultipartUpload(argThat((AbortMultipartUploadRequest request) ->
                    request.bucket().equals("genotype") && request.key().equals("key")
                            && request.uploadId().equals("upload-id")));
            if (failPart) {
                verify(s3, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
            }
        }
    }
}
