package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.*;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;

import java.util.Map;

@Data
@Builder
@ToString
@NoArgsConstructor
public class ProcessedData {
    private Map<String, ImportPreviewStatistics> statistics;
}
