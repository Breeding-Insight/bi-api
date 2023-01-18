package org.breedinginsight.brapps.importer.model.imports;

import org.breedinginsight.dao.db.enums.TermType;
import java.util.Map;
import java.util.Optional;

public class TermTypeTranslator {

    private static final Map<String, TermType> userDisplayToTermTypeMap = Map.of(
            "Phenotype", TermType.PHENOTYPE,
            "Germplasm Attribute", TermType.GERM_ATTRIBUTE,
            "Germplasm Passport", TermType.GERM_PASSPORT);

    public static Optional<TermType> getTermTypeFromUserDisplayName(String userDisplayName) {
        return Optional.ofNullable(userDisplayToTermTypeMap.get(userDisplayName));
    }
}
