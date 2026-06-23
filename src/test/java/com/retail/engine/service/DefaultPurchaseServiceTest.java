package com.retail.engine.service;

import com.retail.engine.model.Order;
import com.retail.engine.model.OrderItem;
import com.retail.engine.model.OrderStatus;
import com.retail.engine.model.Product;
import com.retail.engine.repository.OrderRepository;
import com.retail.engine.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPurchaseServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private DefaultPurchaseService purchaseService;

    private Product buildProduct(Long id, int stock, BigDecimal price) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("Test Product");
        product.setCategory("Electronics");
        product.setPrice(price);
        product.setStock(stock);
        product.setWeightKg(new BigDecimal("1.000"));
        return product;
    }

    @Test
    @DisplayName("Should decrement stock and create a completed order")
    void shouldDecrementStockAndCreateOrder() {
        Product product = buildProduct(1L, 10, new BigDecimal("29.99"));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderRepository.findTopByOrderNumberStartingWithOrderByOrderNumberDesc(startsWith("ORD-")))
                .thenReturn(Optional.empty());

        purchaseService.purchase(1L, 3);

        assertEquals(7, product.getStock());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order order = orderCaptor.getValue();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(new BigDecimal("89.97"), order.getTotalAmount());
        assertTrue(order.getOrderNumber().startsWith("ORD-"));
        assertEquals(1, order.getItems().size());

        OrderItem item = order.getItems().get(0);
        assertEquals(product, item.getProduct());
        assertEquals(3, item.getQuantity());
        assertEquals(new BigDecimal("29.99"), item.getPriceAtPurchase());
    }

    @Test
    @DisplayName("Should allow purchase when quantity equals remaining stock")
    void shouldAllowPurchaseWhenQuantityEqualsStock() {
        Product product = buildProduct(1L, 5, new BigDecimal("10.00"));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderRepository.findTopByOrderNumberStartingWithOrderByOrderNumberDesc(startsWith("ORD-")))
                .thenReturn(Optional.empty());

        purchaseService.purchase(1L, 5);

        assertEquals(0, product.getStock());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw when product is not found")
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        PurchaseException ex = assertThrows(PurchaseException.class,
                () -> purchaseService.purchase(99L, 1));

        assertEquals("Product not found.", ex.getMessage());
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when requested quantity exceeds stock")
    void shouldThrowWhenInsufficientStock() {
        Product product = buildProduct(1L, 2, new BigDecimal("10.00"));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        PurchaseException ex = assertThrows(PurchaseException.class,
                () -> purchaseService.purchase(1L, 3));

        assertEquals("Insufficient stock available.", ex.getMessage());
        assertEquals(2, product.getStock());
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should increment order number from the latest persisted order for the day")
    void shouldIncrementOrderNumberFromLatestPersistedOrder() {
        Product product = buildProduct(1L, 10, new BigDecimal("10.00"));
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Order latestOrder = new Order();
        latestOrder.setOrderNumber("ORD-" + datePart + "-007");

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(orderRepository.findTopByOrderNumberStartingWithOrderByOrderNumberDesc(startsWith("ORD-")))
                .thenReturn(Optional.of(latestOrder));

        purchaseService.purchase(1L, 1);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertTrue(orderCaptor.getValue().getOrderNumber().endsWith("-008"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    @DisplayName("Should throw when quantity is null, zero, or negative")
    void shouldThrowWhenQuantityIsInvalid(Integer quantity) {
        PurchaseException ex = assertThrows(PurchaseException.class,
                () -> purchaseService.purchase(1L, quantity));

        assertEquals("Quantity must be greater than zero.", ex.getMessage());
        verify(productRepository, never()).findByIdForUpdate(any());
    }
}
