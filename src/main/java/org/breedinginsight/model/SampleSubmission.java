/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.geno.BrAPIShipmentForm;
import org.breedinginsight.dao.db.tables.pojos.SampleSubmissionEntity;
import org.jooq.JSONB;

import java.util.List;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@Introspected
@Jacksonized
@JsonIgnoreProperties(value = {"createdBy", "updatedBy", "submittedBy", "shipmentforms"})
public class SampleSubmission extends SampleSubmissionEntity {
    private User createdByUser;
    private User updatedByUser;
    private User submittedByUser;
    private List<BrAPIShipmentForm> shipmentForms;
    private List<BrAPIPlate> plates;
    private List<BrAPISample> samples;

    public SampleSubmission(SampleSubmissionEntity entity) {
        this.setId(entity.getId());
        this.setName(entity.getName());
        this.setSubmitted(entity.getSubmitted());
        this.setSubmittedDate(entity.getSubmittedDate());
        this.setSubmittedBy(entity.getSubmittedBy());
        this.setVendorOrderId(entity.getVendorOrderId());
        this.setVendorStatus(entity.getVendorStatus());
        this.setVendorStatusLastCheck(entity.getVendorStatusLastCheck());
        this.setShipmentforms(entity.getShipmentforms());
        this.setProgramId(entity.getProgramId());
        this.setCreatedAt(entity.getCreatedAt());
        this.setCreatedBy(entity.getCreatedBy());
        this.setUpdatedAt(entity.getUpdatedAt());
        this.setUpdatedBy(entity.getUpdatedBy());

        parseShipmentForms(super.getShipmentforms());
    }

    private void parseShipmentForms(JSONB shipmentforms) {
        if(shipmentforms != null) {
            Gson gson = new Gson();
            this.shipmentForms = gson.fromJson(shipmentforms.data(), new TypeToken<List<BrAPIShipmentForm>>() {}.getType());
        }
    }

    public enum Status {
        NOT_SUBMITTED("NOT SUBMITTED"),
        SUBMITTED("SUBMITTED"),
        COMPLETED("COMPLETED");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Status fromValue(String value) {
            for(Status status : Status.values()) {
                if(status.value.equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }
}
