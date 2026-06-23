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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvImportRowWriterTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DefaultCsvImportRowWriter csvImportRowWriter;

    @Test
    @DisplayName("Should insert a new product row")
    void shouldInsertNewProductRow() {
        when(productRepository.findBySku("RS-001")).thenReturn(Optional.empty());

        csvImportRowWriter.saveRow(
                "RS-001",
                "Running Shoes",
                "Training shoes",
                "Footwear",
                new BigDecimal("89.99"),
                150,
                new BigDecimal("0.35"));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals("RS-001", savedProduct.getSku());
        assertEquals(new BigDecimal("89.99"), savedProduct.getPrice());
        assertEquals(150, savedProduct.getStock());
    }

    @Test
    @DisplayName("Should update an existing product when SKU already exists")
    void shouldUpdateExistingProductWhenSkuExists() {
        Product existingProduct = new Product();
        existingProduct.setSku("RS-001");
        existingProduct.setName("Old Name");
        existingProduct.setStock(10);

        when(productRepository.findBySku("RS-001")).thenReturn(Optional.of(existingProduct));

        csvImportRowWriter.saveRow(
                "RS-001",
                "Running Shoes",
                "Updated Description",
                "Footwear",
                new BigDecimal("94.99"),
                120,
                new BigDecimal("0.35"));

        verify(productRepository).saveAndFlush(existingProduct);
        assertEquals("Running Shoes", existingProduct.getName());
        assertEquals(120, existingProduct.getStock());
        assertEquals(new BigDecimal("94.99"), existingProduct.getPrice());
    }
}
