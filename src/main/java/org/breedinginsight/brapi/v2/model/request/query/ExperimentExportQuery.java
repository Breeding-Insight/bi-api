package org.breedinginsight.brapi.v2.model.request.query;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.jooq.tools.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Getter
@Introspected
public class ExperimentExportQuery {
    private String fileExtension;
    private String dataset;
    private String environments;
    @NotNull
    private String includeTimestamps;

}
