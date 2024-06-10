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
package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPISeason;
import org.breedinginsight.brapi.v2.dao.BrAPISeasonDAO;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
@Slf4j
public class ExperimentSeasonService {

    private final BrAPISeasonDAO brAPISeasonDAO;

    // TODO: move season to actual cache rather than cacheing at application layer
    private Map<String, String> yearToSeasonDbIdCache = new HashMap<>();

    @Inject
    public ExperimentSeasonService(BrAPISeasonDAO brAPISeasonDAO) {
        this.brAPISeasonDAO = brAPISeasonDAO;
    }

    /**
     * Converts year String to SeasonDbId
     * <br>
     * NOTE: This assumes that the only Season records of interest are ones
     * with a blank name or a name that is the same as the year.
     *
     * @param year      The year as a string
     * @param programId the program ID.
     * @return the DbId of the season-record associated with the year
     */
    public String yearToSeasonDbId(String year, UUID programId) {
        String dbID = null;
        if (yearToSeasonDbIdCache.containsKey(year)) { // get it from cache if possible
            dbID = yearToSeasonDbIdCache.get(year);
        } else {
            dbID = yearToSeasonDbIdFromDatabase(year, programId);
            yearToSeasonDbIdCache.put(year, dbID);
        }
        return dbID;
    }

    private String yearToSeasonDbIdFromDatabase(String year, UUID programId) {
        BrAPISeason targetSeason = null;
        List<BrAPISeason> seasons;
        try {
            seasons = brAPISeasonDAO.getSeasonsByYear(year, programId);
            for (BrAPISeason season : seasons) {
                if (null == season.getSeasonName() || season.getSeasonName().isBlank() || season.getSeasonName().equals(year)) {
                    targetSeason = season;
                    break;
                }
            }
            if (targetSeason == null) {
                BrAPISeason newSeason = new BrAPISeason();
                Integer intYear = null;
                if( StringUtils.isNotBlank(year) ){
                    intYear = Integer.parseInt(year);
                }
                newSeason.setYear(intYear);
                newSeason.setSeasonName(year);
                targetSeason = brAPISeasonDAO.addOneSeason(newSeason, programId);
            }

        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            log.error(e.getResponseBody(), e);
        }

        return (targetSeason == null) ? null : targetSeason.getSeasonDbId();
    }
}
