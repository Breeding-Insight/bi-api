package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.*;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;

import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
public class ProcessedData {
    Map<String, ImportPreviewStatistics> statistics;
}
