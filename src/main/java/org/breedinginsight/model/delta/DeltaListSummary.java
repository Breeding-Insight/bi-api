package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;


@Prototype
public class DeltaListSummary extends DeltaEntity<BrAPIListSummary> {

    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaListSummary(BrAPIListSummary brAPIListSummary) { super(brAPIListSummary); }

}
