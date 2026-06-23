package com.retail.engine.service;

import com.retail.engine.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({DefaultCsvImportService.class, DefaultCsvImportRowWriter.class})
class SampleProductsCsvImportTest {

    @Autowired
    private DefaultCsvImportService csvImportService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Should import sample-products.csv with expected success and error counts")
    void shouldImportSampleProductsCsv() throws Exception {
        try (InputStream inputStream = new ClassPathResource("sample-products.csv").getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "sample-products.csv",
                    "text/csv",
                    inputStream.readAllBytes()
            );

            CsvImportResult result = csvImportService.importProducts(file);

            assertEquals(90, result.processedCount());
            assertEquals(5, result.errors().size());
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("Line 16")));
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("Line 25")));
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("Line 41")));
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("Line 50")));
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("Line 52") && error.contains("Category is empty")));
            assertEquals(87, productRepository.count());
        }
    }

    @Test
    @DisplayName("Should upsert duplicate SKUs from sample-products.csv to the latest row values")
    void shouldUpsertDuplicateSkusFromSampleCsv() throws Exception {
        try (InputStream inputStream = new ClassPathResource("sample-products.csv").getInputStream()) {
            csvImportService.importProducts(new MockMultipartFile(
                    "file", "sample-products.csv", "text/csv", inputStream.readAllBytes()));
        }

        productRepository.findBySku("RS-001").ifPresent(product -> {
            assertEquals("Updated lightweight shoes — now with better arch support", product.getDescription());
            assertEquals(new java.math.BigDecimal("94.99"), product.getPrice());
            assertEquals(120, product.getStock());
        });

        productRepository.findBySku("BS-021").ifPresent(product -> {
            assertEquals("Portable waterproof speaker, 10W, 12hr battery", product.getDescription());
            assertEquals(new java.math.BigDecimal("59.99"), product.getPrice());
            assertEquals(110, product.getStock());
        });
    }
}
