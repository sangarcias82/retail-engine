package com.retail.engine.service;

import com.retail.engine.model.Product;
import com.retail.engine.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private ProductRepository productRepository;

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

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        assertTrue(result.errors().isEmpty());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).saveAndFlush(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals("RS-001", savedProduct.getSku());
        assertEquals(new BigDecimal("89.99"), savedProduct.getPrice());
        assertEquals(150, savedProduct.getStock());
    }

    @Test
    @DisplayName("Should strip currency symbol from price and import correctly")
    void shouldCleanCurrencySymbolAndImport() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Wireless Mouse,WM-042,Ergonomic mouse,Electronics,$29.99,75,0.12";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("WM-042")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        assertTrue(result.errors().isEmpty());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(productCaptor.capture());
        assertEquals(new BigDecimal("29.99"), productCaptor.getValue().getPrice());
    }

    @Test
    @DisplayName("Should interpret 'free' price as zero cost")
    void shouldHandleFreePriceAsZero() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Yoga Mat,YM-015,Non-slip mat,Sports,free,200,1.2";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("YM-015")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(productCaptor.capture());
        assertEquals(BigDecimal.ZERO, productCaptor.getValue().getPrice());
    }

    @Test
    @DisplayName("Should interpret uppercase FREE price as zero cost")
    void shouldHandleUppercaseFreePriceAsZero() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Sample Item,SP-001,Sample,Sports,FREE,50,0.5";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("SP-001")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(productCaptor.capture());
        assertEquals(BigDecimal.ZERO, productCaptor.getValue().getPrice());
    }

    @Test
    @DisplayName("Should record error for negative stock row but process valid rows")
    void shouldSkipNegativeStockAndRecordError() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Good shoes,Footwear,89.99,150,0.35\n" +
                "Desk Lamp,DL-007,LED Lamp,Home,45.50,-5,2.1\n" +
                "Backpack,BP-008,Laptop pack,Accessories,64.99,90,0.9";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.empty());
        when(productRepository.findBySku("BP-008")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount(), "Should process only 2 valid products");
        assertEquals(1, result.errors().size(), "Should have exactly 1 recorded error");
        assertTrue(result.errors().get(0).contains("Line 3"), "Error should reference the negative stock line");

        verify(productRepository, times(2)).saveAndFlush(any(Product.class));
    }

    @Test
    @DisplayName("Should update existing product when SKU already exists (Upsert)")
    void shouldUpdateExistingProductWhenSkuExists() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Updated Description,Footwear,94.99,120,0.35";
        MockMultipartFile file = createMockFile(csvContent);

        Product existingProduct = new Product();
        existingProduct.setSku("RS-001");
        existingProduct.setName("Old Name");
        existingProduct.setStock(10);

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.of(existingProduct));

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(1, result.processedCount());
        verify(productRepository).saveAndFlush(existingProduct);
        assertEquals("Running Shoes", existingProduct.getName());
        assertEquals(120, existingProduct.getStock());
    }

    @Test
    @DisplayName("Should skip completely blank rows")
    void shouldSkipBlankRows() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Running Shoes,RS-001,Good shoes,Footwear,89.99,150,0.35\n" +
                ",,,,,,\n" +
                "Backpack,BP-008,Laptop pack,Accessories,64.99,90,0.9";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.empty());
        when(productRepository.findBySku("BP-008")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount());
        assertTrue(result.errors().isEmpty());
        verify(productRepository, times(2)).saveAndFlush(any(Product.class));
    }

    @Test
    @DisplayName("Should return zero processed records for header-only CSV")
    void shouldReturnZeroProcessedForHeaderOnlyCsv() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertTrue(result.errors().isEmpty());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should record error when SKU or name is empty")
    void shouldRecordErrorWhenSkuOrNameIsEmpty() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                ",RS-002,Missing name,Footwear,10.00,5,0.5";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("SKU or name is empty"));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should record error for negative weight")
    void shouldRecordErrorForNegativeWeight() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Heavy Item,HI-001,Too light,Home,10.00,5,-1.0";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertEquals(1, result.errors().size());
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
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Invalid data format"));
    }

    @Test
    @DisplayName("Should record error for non-numeric stock")
    void shouldRecordErrorForNonNumericStock() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "Bad Stock,BS-001,Invalid,Home,10.00,ten,1.0";
        MockMultipartFile file = createMockFile(csvContent);

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(0, result.processedCount());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Invalid data format"));
    }

    @Test
    @DisplayName("Should upsert duplicate SKU within the same CSV file")
    void shouldUpsertDuplicateSkuWithinSameFile() {
        String csvContent = "name,sku,description,category,price,stock,weight_kg\n" +
                "First Name,RS-001,First,Footwear,10.00,5,0.5\n" +
                "Second Name,RS-001,Second,Footwear,20.00,8,0.8";
        MockMultipartFile file = createMockFile(csvContent);

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.empty());

        CsvImportResult result = csvImportService.importProducts(file);

        assertEquals(2, result.processedCount());
        assertTrue(result.errors().isEmpty());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).saveAndFlush(productCaptor.capture());

        Product finalProduct = productCaptor.getAllValues().get(1);
        assertEquals("Second Name", finalProduct.getName());
        assertEquals(new BigDecimal("20.00"), finalProduct.getPrice());
        assertEquals(8, finalProduct.getStock());
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
    void shouldThrowWhenCsvFileCannotBeRead() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("Simulated I/O failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> csvImportService.importProducts(file));
        assertTrue(ex.getMessage().contains("Failed to process CSV file"));
    }
}
