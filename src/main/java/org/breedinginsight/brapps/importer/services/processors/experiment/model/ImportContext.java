package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import tech.tablesaw.api.Table;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ImportContext {
    private ImportUpload upload;
    private List<BrAPIImport> importRows;
    private Map<Integer, PendingImport> mappedBrAPIImport;
    private Table data;
    private Program program;
    private User user;
    private boolean commit;
}
