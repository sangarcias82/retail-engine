package com.retail.engine.service;

import com.retail.engine.dto.ProductRequest;
import com.retail.engine.dto.UpdateProductRequest;
import com.retail.engine.model.Product;
import com.retail.engine.repository.OrderItemRepository;
import com.retail.engine.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DefaultProductService implements ProductService {

    static final int DEFAULT_PAGE_SIZE = 12;
    static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public DefaultProductService(ProductRepository productRepository, OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public Page<Product> searchProducts(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("name").ascending());

        if (search == null || search.isBlank()) {
            return productRepository.findAll(pageable);
        }

        String term = escapeLikeTerm(search.trim());
        return productRepository.searchByNameOrSku(term, term, pageable);
    }

    public static String escapeLikeTerm(String term) {
        return term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @Override
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        product.setSku(request.sku());
        applyMutableFields(product, toUpdateRequest(request));
        return productRepository.save(product);
    }

    @Override
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);
        applyMutableFields(product, request);
        return productRepository.save(product);
    }

    @Override
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete product because it has existing purchase history.");
        }
        productRepository.deleteById(id);
    }

    private void applyMutableFields(Product product, UpdateProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setWeightKg(request.weightKg());
    }

    private UpdateProductRequest toUpdateRequest(ProductRequest request) {
        return new UpdateProductRequest(
                request.name(),
                request.description(),
                request.category(),
                request.price(),
                request.stock(),
                request.weightKg());
    }
}
