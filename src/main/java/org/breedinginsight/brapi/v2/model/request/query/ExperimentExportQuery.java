package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;
import org.breedinginsight.brapps.importer.model.exports.FileType;

import javax.validation.constraints.NotNull;

@Getter
@Introspected
@ToString
public class ExperimentExportQuery {
    private FileType fileExtension;
    private String dataset;  // TODO: rename so it's clear this is dataset NAME, not ID. Also change on frontend.
    private String environments;
    @NotNull
    private boolean includeTimestamps;

}
