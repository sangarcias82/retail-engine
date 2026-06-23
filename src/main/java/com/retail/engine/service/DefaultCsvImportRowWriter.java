package com.retail.engine.service;

import com.retail.engine.model.Product;
import com.retail.engine.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DefaultCsvImportRowWriter implements CsvImportRowWriter {

    private final ProductRepository productRepository;

    public DefaultCsvImportRowWriter(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRow(String sku,
                        String name,
                        String description,
                        String category,
                        BigDecimal price,
                        Integer stock,
                        BigDecimal weight) {
        Product product = productRepository.findBySku(sku).orElse(new Product());

        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setCategory(category);
        product.setPrice(price);
        product.setStock(stock);
        product.setWeightKg(weight);

        productRepository.saveAndFlush(product);
    }
}
