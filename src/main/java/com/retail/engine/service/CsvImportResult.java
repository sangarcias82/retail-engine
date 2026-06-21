package com.retail.engine.service;

import java.util.List;

public record CsvImportResult(int processedCount, List<String> errors) {
}
