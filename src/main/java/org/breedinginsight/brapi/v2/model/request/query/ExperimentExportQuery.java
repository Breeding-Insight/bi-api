package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.brapps.importer.model.exports.FileType;

import javax.validation.constraints.NotNull;

@Getter
@Introspected
public class ExperimentExportQuery {
    private FileType fileExtension;
    private String dataset;
    private String environments;
    @NotNull
    private boolean includeTimestamps;

}
