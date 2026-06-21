package com.retail.engine.repository;

import com.retail.engine.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    private Product buildProduct(String sku, String name) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription("Description for " + name);
        product.setCategory("Electronics");
        product.setPrice(new BigDecimal("19.99"));
        product.setStock(100);
        product.setWeightKg(new BigDecimal("0.500"));
        return product;
    }

    @Test
    @DisplayName("Should persist product with created and updated timestamps")
    void shouldPersistProductWithTimestamps() {
        Product saved = productRepository.save(buildProduct("TS-001", "Timestamp Product"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("Should find products by name or SKU using case-insensitive partial match")
    void shouldFindProductsByNameOrSku() {
        productRepository.save(buildProduct("WM-042", "Wireless Mouse"));
        productRepository.save(buildProduct("RS-001", "Running Shoes"));

        List<Product> byName = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase("mouse", "mouse");
        assertEquals(1, byName.size());
        assertEquals("WM-042", byName.get(0).getSku());

        List<Product> bySku = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase("RS-001", "RS-001");
        assertEquals(1, bySku.size());
        assertEquals("Running Shoes", bySku.get(0).getName());
    }

    @Test
    @DisplayName("Should increment version on each update")
    void shouldIncrementVersionOnEachUpdate() {
        Product saved = productRepository.save(buildProduct("VER-001", "Version Product"));
        Long initialVersion = saved.getVersion();

        saved.setStock(50);
        Product updated = productRepository.saveAndFlush(saved);

        assertNotNull(initialVersion);
        assertTrue(updated.getVersion() > initialVersion);
    }

    @Test
    @DisplayName("Should find product by unique SKU")
    void shouldFindProductBySku() {
        productRepository.save(buildProduct("UNQ-001", "Unique SKU Product"));

        assertTrue(productRepository.findBySku("UNQ-001").isPresent());
        assertTrue(productRepository.findBySku("MISSING").isEmpty());
    }
}
