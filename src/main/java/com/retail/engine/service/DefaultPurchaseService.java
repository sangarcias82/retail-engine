package com.retail.engine.service;

import com.retail.engine.model.Order;
import com.retail.engine.model.OrderItem;
import com.retail.engine.model.OrderStatus;
import com.retail.engine.model.Product;
import com.retail.engine.repository.OrderRepository;
import com.retail.engine.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class DefaultPurchaseService implements PurchaseService {

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

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new PurchaseException("Product not found."));

        if (product.getStock() < quantity) {
            throw new PurchaseException("Insufficient stock available.");
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.COMPLETED);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPriceAtPurchase(product.getPrice());
        order.addItem(item);

        orderRepository.save(order);
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "ORD-" + datePart + "-";
        long sequence = orderRepository.findTopByOrderNumberStartingWithOrderByOrderNumberDesc(prefix)
                .map(Order::getOrderNumber)
                .map(orderNumber -> orderNumber.substring(prefix.length()))
                .map(this::parseOrderSequence)
                .orElse(0L) + 1;
        return prefix + String.format("%03d", sequence);
    }

    private long parseOrderSequence(String suffix) {
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
