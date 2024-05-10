package org.breedinginsight.brapps.importer.model.imports;

import lombok.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import tech.tablesaw.api.Table;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ImportServiceContext {
    private UUID workflowId;
    private List<BrAPIImport> brAPIImports;
    private Table data;
    private Program program;
    private ImportUpload upload;
    private User user;
    private boolean commit;
}
