package com.retail.engine.service;

import com.retail.engine.dto.ProductRequest;
import com.retail.engine.dto.UpdateProductRequest;
import com.retail.engine.model.Product;
import com.retail.engine.repository.OrderItemRepository;
import com.retail.engine.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private DefaultProductService productService;

    @Test
    @DisplayName("Should return paginated catalog when search is blank")
    void shouldReturnPaginatedCatalogWhenSearchIsBlank() {
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Product> result = productService.searchProducts("   ", 0, 12);

        assertEquals(page, result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(12, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("Should search with pagination when term is provided")
    void shouldSearchWithPaginationWhenTermIsProvided() {
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.searchByNameOrSku(eq("mouse"), eq("mouse"), any(Pageable.class)))
                .thenReturn(page);

        Page<Product> result = productService.searchProducts(" mouse ", 2, 5);

        assertEquals(page, result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).searchByNameOrSku(
                eq("mouse"), eq("mouse"), pageableCaptor.capture());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("Should clamp invalid page and size values")
    void shouldClampInvalidPageAndSizeValues() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        productService.searchProducts(null, -3, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(DefaultProductService.MAX_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("Should preserve SKU on update even when request includes a different SKU")
    void shouldPreserveSkuOnUpdate() {
        Product existing = new Product();
        existing.setId(1L);
        existing.setSku("RS-001");
        existing.setName("Running Shoes");
        existing.setCategory("Footwear");
        existing.setPrice(new BigDecimal("89.99"));
        existing.setStock(10);
        existing.setWeightKg(new BigDecimal("0.350"));

        ProductRequest updateRequest = new ProductRequest(
                "NEW-SKU",
                "Updated Shoes",
                "Updated",
                "Footwear",
                new BigDecimal("99.99"),
                5,
                new BigDecimal("0.400"));

        UpdateProductRequest updateBody = new UpdateProductRequest(
                updateRequest.name(),
                updateRequest.description(),
                updateRequest.category(),
                updateRequest.price(),
                updateRequest.stock(),
                updateRequest.weightKg());

        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        Product updated = productService.updateProduct(1L, updateBody);

        assertEquals("RS-001", updated.getSku());
        assertEquals("Updated Shoes", updated.getName());
        assertEquals(new BigDecimal("99.99"), updated.getPrice());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertEquals("RS-001", productCaptor.getValue().getSku());
    }

    @Test
    @DisplayName("Should escape LIKE wildcard characters before searching")
    void shouldEscapeLikeWildcardCharactersBeforeSearching() {
        assertEquals("100\\%", DefaultProductService.escapeLikeTerm("100%"));
        assertEquals("a\\_b", DefaultProductService.escapeLikeTerm("a_b"));

        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.searchByNameOrSku(eq("100\\%"), eq("100\\%"), any(Pageable.class)))
                .thenReturn(page);

        productService.searchProducts("100%", 0, 12);

        verify(productRepository).searchByNameOrSku(eq("100\\%"), eq("100\\%"), any(Pageable.class));
    }

    @Test
    @DisplayName("Should reject create when SKU already exists")
    void shouldRejectCreateWhenSkuAlreadyExists() {
        ProductRequest request = new ProductRequest(
                "RS-001", "Running Shoes", "Desc", "Footwear",
                new BigDecimal("89.99"), 10, new BigDecimal("0.350"));
        when(productRepository.existsBySku("RS-001")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.createProduct(request));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("A product with SKU 'RS-001' already exists.", ex.getReason());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject delete when product has purchase history")
    void shouldRejectDeleteWhenProductHasPurchaseHistory() {
        when(productRepository.existsById(18L)).thenReturn(true);
        when(orderItemRepository.existsByProduct_Id(18L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.deleteProduct(18L));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("Cannot delete product because it has existing purchase history.", ex.getReason());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should delete product when no purchase history exists")
    void shouldDeleteProductWhenNoPurchaseHistoryExists() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(orderItemRepository.existsByProduct_Id(1L)).thenReturn(false);

        productService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
    }
}
