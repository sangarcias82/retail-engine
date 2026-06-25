package com.retail.engine.service;

import com.retail.engine.exception.CsvImportException;
import com.retail.engine.repository.ProductRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultCsvImportService implements CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCsvImportService.class);

    private final CsvImportRowWriter csvImportRowWriter;

    public DefaultCsvImportService(CsvImportRowWriter csvImportRowWriter) {
        this.csvImportRowWriter = csvImportRowWriter;
    }

    @Override
    public CsvImportResult importProducts(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int processedCount = 0;

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            for (CSVRecord record : csvParser) {
                long lineNumber = record.getRecordNumber() + 1;
                try {
                    if (isBlankRow(record)) {
                        continue;
                    }

                    String sku = record.get("sku");
                    String name = record.get("name");

                    if (sku == null || sku.isBlank() || name == null || name.isBlank()) {
                        errors.add("Line " + lineNumber + ": SKU or name is empty.");
                        continue;
                    }

                    String category = record.get("category");
                    if (category == null || category.isBlank()) {
                        errors.add("Line " + lineNumber + ": Category is empty.");
                        continue;
                    }

                    BigDecimal price = parsePrice(record.get("price"), lineNumber, errors);
                    if (price == null) {
                        continue;
                    }

                    Integer stock = parseStock(record.get("stock"), lineNumber, errors);
                    if (stock == null) {
                        continue;
                    }
                    if (stock < 0) {
                        errors.add("Line " + lineNumber + ": Stock cannot be negative (" + stock + ").");
                        continue;
                    }

                    BigDecimal weight = parseWeight(record.get("weight_kg"), lineNumber, errors);
                    if (weight == null) {
                        continue;
                    }

                    csvImportRowWriter.saveRow(
                            sku,
                            name,
                            record.get("description"),
                            category,
                            price,
                            stock,
                            weight);
                    processedCount++;

                } catch (Exception e) {
                    log.error("Error processing line {}", lineNumber, e);
                    errors.add("Line " + lineNumber + ": Invalid data format (" + e.getMessage() + ").");
                }
            }

        } catch (Exception e) {
            log.error("Critical error reading CSV file", e);
            throw new CsvImportException("Failed to process CSV file: " + e.getMessage(), e);
        }

        return new CsvImportResult(processedCount, errors);
    }

    private BigDecimal parsePrice(String rawPrice, long lineNumber, List<String> errors) {
        String priceStr = rawPrice == null ? "" : rawPrice.replace("$", "").trim();
        if (priceStr.isBlank()) {
            errors.add("Line " + lineNumber + ": Price is empty.");
            return null;
        }
        if (priceStr.equalsIgnoreCase("free")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(priceStr);
        } catch (NumberFormatException ex) {
            errors.add("Line " + lineNumber + ": Price must be a valid number.");
            return null;
        }
    }

    private Integer parseStock(String rawStock, long lineNumber, List<String> errors) {
        String stockStr = rawStock == null ? "" : rawStock.trim();
        if (stockStr.isBlank()) {
            errors.add("Line " + lineNumber + ": Stock is empty.");
            return null;
        }
        if (stockStr.contains(".")) {
            errors.add("Line " + lineNumber + ": Stock must be a valid whole number.");
            return null;
        }
        try {
            return Integer.parseInt(stockStr);
        } catch (NumberFormatException ex) {
            errors.add("Line " + lineNumber + ": Stock must be a valid whole number.");
            return null;
        }
    }

    private BigDecimal parseWeight(String rawWeight, long lineNumber, List<String> errors) {
        String weightStr = rawWeight == null ? "" : rawWeight.trim();
        if (weightStr.isBlank()) {
            errors.add("Line " + lineNumber + ": Weight is empty.");
            return null;
        }
        try {
            BigDecimal weight = new BigDecimal(weightStr);
            if (weight.compareTo(BigDecimal.ZERO) < 0) {
                errors.add("Line " + lineNumber + ": Weight cannot be negative (" + weight + ").");
                return null;
            }
            return weight;
        } catch (NumberFormatException ex) {
            errors.add("Line " + lineNumber + ": Weight must be a valid number.");
            return null;
        }
    }

    private boolean isBlankRow(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
