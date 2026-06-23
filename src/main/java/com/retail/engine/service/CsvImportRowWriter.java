package com.retail.engine.service;

import java.math.BigDecimal;

public interface CsvImportRowWriter {

    void saveRow(String sku,
                 String name,
                 String description,
                 String category,
                 BigDecimal price,
                 Integer stock,
                 BigDecimal weight);
}
