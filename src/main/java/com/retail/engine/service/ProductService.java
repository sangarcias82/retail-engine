package com.retail.engine.service;

import com.retail.engine.dto.ProductRequest;
import com.retail.engine.model.Product;

import java.util.List;

public interface ProductService {

    List<Product> searchProducts(String search);

    Product getProduct(Long id);

    Product createProduct(ProductRequest request);

    Product updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
