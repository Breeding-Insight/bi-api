package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.model.request.query.GermplasmQuery;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;
import org.breedinginsight.utilities.response.mappers.GermplasmQueryMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


@Prototype
public class DeltaGermplasmListDetails extends DeltaListDetails<BrAPIGermplasm> {

    private final BrAPIGermplasmService germplasmService;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaGermplasmListDetails(BrAPIListDetails brAPIListDetails,
                              String referenceSourceBase,
                              BrAPIGermplasmService germplasmService) {
        super(brAPIListDetails, referenceSourceBase);
        this.germplasmService = germplasmService;
    }

    @Override
    public List<BrAPIGermplasm> getDataObjects() throws ApiException, UnprocessableEntityException {
        UUID programId = getProgramId().orElseThrow(() -> new UnprocessableEntityException("Program Id not found for list " + getListName()));
        return germplasmService.getGermplasmByList(programId, getListDbId());
    }

    @Override
    public DownloadFile exportListObjects(FileType extension) throws IllegalArgumentException, ApiException, IOException, DoesNotExistException, UnprocessableEntityException {
        UUID programId = getProgramId().orElseThrow(() -> new UnprocessableEntityException("Program Id not found for list " + getListName()));
        return germplasmService.exportGermplasmList(programId, getListDbId(), extension);
    }

    @Override
    public AbstractQueryMapper getQueryMapper() {
        return new GermplasmQueryMapper();
    }

    @Override
    public BrapiQuery getQuery() {
        return new GermplasmQuery();
    }

    @Override
    public SearchRequest constructSearchRequest(BrapiQuery queryParams) {
        return ((GermplasmQuery) queryParams).constructSearchRequest();
    }
}
