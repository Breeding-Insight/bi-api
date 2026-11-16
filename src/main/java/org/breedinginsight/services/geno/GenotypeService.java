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
package org.breedinginsight.services.geno;

import io.micronaut.http.multipart.CompletedFileUpload;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.model.GenotypeImportDetails;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenotypeService {
    ImportResponse submitGenotypeData(UUID userId, UUID programId, UUID submissionId, CompletedFileUpload uploadedFile) throws DoesNotExistException, AuthorizationException, ApiException;

    GermplasmGenotype retrieveGenotypeData(UUID programId, UUID germplasmId) throws DoesNotExistException, AuthorizationException, ApiException;

    List<GenotypeImportDetails> getGenotypeImports(UUID programId);

    Optional<DownloadFile> downloadGenotypeImport(UUID programId, UUID genotypeImportId)throws IOException;
}
