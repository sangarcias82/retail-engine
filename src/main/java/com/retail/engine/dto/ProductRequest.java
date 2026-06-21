package com.retail.engine.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "SKU is required") String sku,
        @NotBlank(message = "Name is required") String name,
        String description,
        @NotBlank(message = "Category is required") String category,
        @NotNull(message = "Price is required") @DecimalMin(value = "0.00", message = "Price cannot be negative") BigDecimal price,
        @NotNull(message = "Stock is required") @Min(value = 0, message = "Stock cannot be less than zero") Integer stock,
        @NotNull(message = "Weight is required") @DecimalMin(value = "0.000", message = "Weight cannot be negative") BigDecimal weightKg
) {
}
