package org.breedinginsight.brapps.importer.services.processors.experiment.create.model;

import lombok.*;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;

import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ExistingData {
    private Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope;
    // TODO: add rest of fields
}
