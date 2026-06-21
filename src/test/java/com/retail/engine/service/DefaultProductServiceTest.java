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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProductServiceTest {

    @Mock
    private ProductRepository productRepository;

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
        when(productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(eq("mouse"), eq("mouse"), any(Pageable.class)))
                .thenReturn(page);

        Page<Product> result = productService.searchProducts(" mouse ", 2, 5);

        assertEquals(page, result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
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
}
