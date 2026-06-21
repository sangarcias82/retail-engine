package com.retail.engine.service;

import com.retail.engine.model.Order;
import com.retail.engine.model.OrderItem;
import com.retail.engine.model.OrderStatus;
import com.retail.engine.model.Product;
import com.retail.engine.repository.OrderRepository;
import com.retail.engine.repository.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DefaultPurchaseService implements PurchaseService {

    private static final AtomicLong ORDER_SEQUENCE = new AtomicLong(1);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public DefaultPurchaseService(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void purchase(Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new PurchaseException("Quantity must be greater than zero.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new PurchaseException("Product not found."));

        if (product.getStock() < quantity) {
            throw new PurchaseException("Insufficient stock available.");
        }

        try {
            product.setStock(product.getStock() - quantity);
            productRepository.save(product);

            Order order = new Order();
            order.setOrderNumber(generateOrderNumber());
            order.setStatus(OrderStatus.COMPLETED);
            order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(quantity)));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setPriceAtPurchase(product.getPrice());
            order.addItem(item);

            orderRepository.save(order);
        } catch (OptimisticLockException ex) {
            throw new PurchaseException(
                    "Insufficient stock available or inventory changed. Please try again.");
        }
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long sequence = ORDER_SEQUENCE.getAndIncrement();
        return "ORD-" + datePart + "-" + String.format("%03d", sequence);
    }
}
