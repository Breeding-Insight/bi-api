package org.breedinginsight.services.geno;

import io.micronaut.http.multipart.CompletedFileUpload;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.model.GenotypeImportDetails;

import java.util.List;
import java.util.UUID;

public interface GenotypeService {
    ImportResponse submitGenotypeData(UUID userId, UUID programId, UUID submissionId, CompletedFileUpload uploadedFile) throws DoesNotExistException, AuthorizationException, ApiException;

    GermplasmGenotype retrieveGenotypeData(UUID programId, BrAPIGermplasm germplasm) throws DoesNotExistException, AuthorizationException, ApiException;

    List<GenotypeImportDetails> getGenotypeImports(UUID programId);
}
