package com.retail.engine.service;

import org.springframework.web.multipart.MultipartFile;

public interface CsvImportService {

    CsvImportResult importProducts(MultipartFile file);
}
