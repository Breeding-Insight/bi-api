package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;


@Prototype
public class DeltaGermplasmListSummary extends DeltaListSummary {

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    DeltaGermplasmListSummary(BrAPIListSummary brAPIListSummary) { super(brAPIListSummary); }

}
