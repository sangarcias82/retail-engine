package com.retail.engine.service;

import com.retail.engine.model.Product;
import com.retail.engine.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultCsvImportService implements CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCsvImportService.class);
    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DefaultCsvImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
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

                    String priceStr = record.get("price").replace("$", "").trim();
                    if (priceStr.isBlank()) {
                        errors.add("Line " + lineNumber + ": Price is empty.");
                        continue;
                    }
                    BigDecimal price = priceStr.equalsIgnoreCase("free")
                            ? BigDecimal.ZERO
                            : new BigDecimal(priceStr);

                    Integer stock = Integer.parseInt(record.get("stock").trim());
                    if (stock < 0) {
                        errors.add("Line " + lineNumber + ": Stock cannot be negative (" + stock + ").");
                        continue;
                    }

                    String weightStr = record.get("weight_kg").trim();
                    if (weightStr.isBlank()) {
                        errors.add("Line " + lineNumber + ": Weight is empty.");
                        continue;
                    }
                    BigDecimal weight = new BigDecimal(weightStr);
                    if (weight.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Line " + lineNumber + ": Weight cannot be negative (" + weight + ").");
                        continue;
                    }

                    Optional<Product> existingProductOpt = productRepository.findBySku(sku);
                    Product product = existingProductOpt.orElse(new Product());

                    product.setSku(sku);
                    product.setName(name);
                    product.setDescription(record.get("description"));
                    product.setCategory(category);
                    product.setPrice(price);
                    product.setStock(stock);
                    product.setWeightKg(weight);

                    productRepository.saveAndFlush(product);
                    processedCount++;

                } catch (Exception e) {
                    log.error("Error processing line {}", lineNumber, e);
                    errors.add("Line " + lineNumber + ": Invalid data format (" + e.getMessage() + ").");
                    if (entityManager != null) {
                        entityManager.clear();
                    }
                }
            }

        } catch (Exception e) {
            log.error("Critical error reading CSV file", e);
            throw new RuntimeException("Failed to process CSV file: " + e.getMessage());
        }

        return new CsvImportResult(processedCount, errors);
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
