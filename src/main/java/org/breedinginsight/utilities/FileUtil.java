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

package org.breedinginsight.utilities;

import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.services.parsers.excel.ExcelParser;
import org.breedinginsight.services.parsers.excel.ExcelRecord;
import org.breedinginsight.services.parsers.trait.TraitFileColumns;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class FileUtil {

    public static Table parseTableFromExcel(CompletedFileUpload file) {
        return Table.create();
    }

    public static Table parseTableFromCsv(InputStream inputStream) throws ParsingException {
        //TODO: See if this has the windows BOM issue
        try {
            Table df = Table.read().csv(inputStream);
            return df;
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }
    }

}
