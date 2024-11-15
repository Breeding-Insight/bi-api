package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import io.micronaut.context.annotation.Property;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


@Prototype
public abstract class DeltaListDetails<T> extends DeltaEntity<BrAPIListDetails> {
    private final String referenceSourceBase;
    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaListDetails(BrAPIListDetails brAPIListDetails, String referenceSourceBase) {
        super(brAPIListDetails);
        this.referenceSourceBase = referenceSourceBase;
    }

    public abstract List<T> getDataObjects() throws ApiException, UnprocessableEntityException;
    public abstract DownloadFile exportListObjects(FileType extension) throws IllegalArgumentException, ApiException, IOException, DoesNotExistException, UnprocessableEntityException;
    public abstract AbstractQueryMapper getQueryMapper();

    public abstract BrapiQuery getQuery();

    public abstract SearchRequest constructSearchRequest(BrapiQuery queryParams);

    public BrAPIListTypes getListType() {
        return entity.getListType();
    }

    public Optional<UUID> getListId() {
        return getXrefId(ExternalReferenceSource.LISTS);
    }

    public Optional<UUID> getProgramId() {
        return getXrefId(ExternalReferenceSource.PROGRAMS);
    }

    public String getListDbId() {
        return entity.getListDbId();
    }

    public String getListName() {
        return entity.getListName();
    }

    private Optional<UUID> getXrefId(ExternalReferenceSource source) {
        // Get the external reference if it exists
        Optional<BrAPIExternalReference> xrefOptional = Utilities.getExternalReference(entity.getExternalReferences(), referenceSourceBase, source);

        // Parse the Deltabreed ID from the xref
        return xrefOptional.map(BrAPIExternalReference::getReferenceId).map(UUID::fromString);
    }
}
