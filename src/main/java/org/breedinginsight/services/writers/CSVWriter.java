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

package org.breedinginsight.services.writers;

import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.model.Column;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/*
 * This csv writer creates a csv file with the first row as column names and subsequent rows
 * as data.
 */
@Slf4j
public class CSVWriter extends FileWriter {

    public CSVWriter(@NotNull String fileName) throws IOException {
        super(fileName);
    }

    public static StreamedFile writeToDownload(List<Column> columns, List<Map<String, Object>> data, FileType extension) throws IOException {
        try (ByteArrayOutputStream out = writeToCSV(columns, data)) {
            InputStream inputStream = new ByteArrayInputStream(out.toByteArray());
            MediaType fileVal = new MediaType(extension.getMimeType(), extension.getName());
            StreamedFile downloadFile = new StreamedFile(inputStream, fileVal);
            return downloadFile;
        } catch (IOException e) {
            log.info(e.getMessage());
            throw e;
        }
    }

    //Writes to csv with desired columns and data
    public static ByteArrayOutputStream writeToCSV(List<Column> columns, List<Map<String, Object>> data) {

        String[] headers = columns.stream().map(x -> x.getValue() ).toArray(String[]::new);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            CSVFormat format = CSVFormat.EXCEL.withHeader(headers);
            CSVPrinter csvPrinter = new CSVPrinter(new PrintWriter(out), format);

            ArrayList<Object> rowVals;
            Object cellValue;
            for (int i = 0; i < data.size(); i++) {
                rowVals = new ArrayList<>();
                for (Column column: columns) {
                    if(data.get(i).containsKey(column.getValue())){
                        cellValue = data.get(i).get(column.getValue());
                        if (cellValue instanceof Double) { cellValue = ((Double) cellValue).intValue(); }
                        rowVals.add(cellValue);
                    } else {
                     rowVals.add("");
                    }
                }
                csvPrinter.printRecord(rowVals);
            }
            csvPrinter.close();
        } catch (IOException e) {

        }
        return out;
    }
}
