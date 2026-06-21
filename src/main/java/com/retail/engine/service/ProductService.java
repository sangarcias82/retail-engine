package com.retail.engine.service;

import com.retail.engine.dto.ProductRequest;
import com.retail.engine.model.Product;
import org.springframework.data.domain.Page;

public interface ProductService {

    Page<Product> searchProducts(String search, int page, int size);

    Product getProduct(Long id);

    Product createProduct(ProductRequest request);

    Product updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
