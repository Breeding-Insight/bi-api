package org.breedinginsight.brapps.importer.services.processors.experiment;

import java.util.Optional;

public enum WorkflowEnum {
    NEW_OBSERVATION("new-experiment","Create new experiment"),
    APPEND_OVERWRITE("append-dataset", "Append experimental dataset");

    private String id;
    private String name;

    WorkflowEnum(String id, String name) {

        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }
    public String getName() { return name; }

    public boolean isEqual(String value) {
        return Optional.ofNullable(id.equals(value)).orElse(false);
    }
}