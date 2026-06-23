package com.retail.engine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private CsvImportRowWriter csvImportRowWriter;

    @InjectMocks
    private DefaultCsvImportService csvImportService;

    private MockMultipartFile createMockFile(String content) {
        return new MockMultipartFile(
                "file",
                "test.csv",
                "text/csv",
                content.getBytes()
        );
    }

    @Test
    @DisplayName("Should import a valid product successfully")
    void shouldImportValidProduct() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Training shoes,Footwear,89.99,150,0.35";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        assertTrue(result.errors().isEmpty());
        verify(csvImportRowWriter).saveRow(
                eq("RS-001"),
                eq("Running Shoes"),
                eq("Training shoes"),
                eq("Footwear"),
                eq(new BigDecimal("89.99")),
                eq(150),
                eq(new BigDecimal("0.35")));
    }

    @Test
    @DisplayName("Should strip currency symbol from price and import correctly")
    void shouldCleanCurrencySymbolAndImport() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Wireless Mouse,WM-042,Ergonomic mouse,Electronics,$29.99,75,0.12";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        verify(csvImportRowWriter).saveRow(
                eq("WM-042"), anyString(), anyString(), eq("Electronics"),
                eq(new BigDecimal("29.99")), eq(75), eq(new BigDecimal("0.12")));
    }

    @Test
    @DisplayName("Should interpret 'free' price as zero cost")
    void shouldHandleFreePriceAsZero() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Yoga Mat,YM-015,Non-slip mat,Sports,free,200,1.2";
        MockMultipartFile file = createMockFile(csvContent);

        csvImportService.importProducts(file);

        verify(csvImportRowWriter).saveRow(
                eq("YM-015"), anyString(), anyString(), eq("Sports"),
                eq(BigDecimal.ZERO), eq(200), eq(new BigDecimal("1.2")));
    }

    @Test
    @DisplayName("Should interpret uppercase FREE price as zero cost")
    void shouldHandleUppercaseFreePriceAsZero() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Sample Item,SP-001,Sample,Sports,FREE,50,0.5";
        MockMultipartFile file = createMockFile(csvContent);

        csvImportService.importProducts(file);

        verify(csvImportRowWriter).saveRow(
                eq("SP-001"), anyString(), anyString(), eq("Sports"),
                eq(BigDecimal.ZERO), eq(50), eq(new BigDecimal("0.5")));
    }

    @Test
    @DisplayName("Should record error for negative stock row but process valid rows")
    void shouldSkipNegativeStockAndRecordError() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Good shoes,Footwear,89.99,150,0.35\n" +
                "Desk Lamp,DL-007,LED Lamp,Home,45.50,-5,2.1\n" +
                "Backpack,BP-008,Laptop pack,Accessories,64.99,90,0.9";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Line 3"));
        verify(csvImportRowWriter, times(2)).saveRow(anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyInt(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should delegate upsert rows to the row writer")
    void shouldDelegateUpsertRowsToRowWriter() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Updated Description,Footwear,94.99,120,0.35";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        verify(csvImportRowWriter).saveRow(
                eq("RS-001"), eq("Running Shoes"), eq("Updated Description"), eq("Footwear"),
                eq(new BigDecimal("94.99")), eq(120), eq(new BigDecimal("0.35")));
    }

    @Test
    @DisplayName("Should skip completely blank rows")
    void shouldSkipBlankRows() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Good shoes,Footwear,89.99,150,0.35\n" +
                ",,,,,,\n" +
                "Backpack,BP-008,Laptop pack,Accessories,64.99,90,0.9";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount());
        assertTrue(result.errors().isEmpty());
        verify(csvImportRowWriter, times(2)).saveRow(anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyInt(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should return zero processed records for header-only CSV")
    void shouldReturnZeroProcessedForHeaderOnlyCsv() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        verify(csvImportRowWriter, never()).saveRow(anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyInt(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should record error when SKU or name is empty")
    void shouldRecordErrorWhenSkuOrNameIsEmpty() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                ",RS-002,Missing name,Footwear,10.00,5,0.5";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().get(0).contains("SKU or name is empty"));
        verify(csvImportRowWriter, never()).saveRow(anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyInt(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should record error for negative weight")
    void shouldRecordErrorForNegativeWeight() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Heavy Item,HI-001,Too light,Home,10.00,5,-1.0";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().get(0).contains("Weight cannot be negative"));
    }

    @Test
    @DisplayName("Should record error for invalid price format")
    void shouldRecordErrorForInvalidPriceFormat() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Bad Price,BP-001,Invalid,Home,not-a-price,5,1.0";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().get(0).contains("Price must be a valid number"));
    }

    @Test
    @DisplayName("Should record error for non-numeric stock")
    void shouldRecordErrorForNonNumericStock() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Bad Stock,BS-001,Invalid,Home,10.00,ten,1.0";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().get(0).contains("Stock must be a valid whole number"));
    }

    @Test
    @DisplayName("Should continue processing when a row writer failure is isolated")
    void shouldContinueProcessingWhenRowWriterFailureIsIsolated() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Good shoes,Footwear,89.99,150,0.35\n" +
                "Backpack,BP-008,Laptop pack,Accessories,64.99,90,0.9";
        MockMultipartFile file = createMockFile(csvContent);

        doThrow(new RuntimeException("Simulated DB failure"))
                .when(csvImportRowWriter).saveRow(
                        eq("RS-001"), anyString(), anyString(), anyString(),
                        any(BigDecimal.class), anyInt(), any(BigDecimal.class));

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Line 2"));
        verify(csvImportRowWriter, times(2)).saveRow(anyString(), anyString(), anyString(), anyString(),
                any(BigDecimal.class), anyInt(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Should upsert duplicate SKU within the same CSV file")
    void shouldUpsertDuplicateSkuWithinSameFile() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "First Name,RS-001,First,Footwear,10.00,5,0.5\n" +
                "Second Name,RS-001,Second,Footwear,20.00,8,0.8";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount());
        verify(csvImportRowWriter).saveRow(
                eq("RS-001"), eq("First Name"), eq("First"), eq("Footwear"),
                eq(new BigDecimal("10.00")), eq(5), eq(new BigDecimal("0.5")));
        verify(csvImportRowWriter).saveRow(
                eq("RS-001"), eq("Second Name"), eq("Second"), eq("Footwear"),
                eq(new BigDecimal("20.00")), eq(8), eq(new BigDecimal("0.8")));
    }

    @Test
    @DisplayName("Should return zero processed records for empty CSV file")
    void shouldReturnZeroProcessedForEmptyCsvFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should throw when CSV file cannot be read")
    void shouldThrowWhenCsvFileCannotBeRead() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getInputStream()).thenThrow(new java.io.IOException("Simulated I/O failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> csvImportService.importProducts(file));
        assertTrue(ex.getMessage().contains("Failed to process CSV file"));
    }
}
