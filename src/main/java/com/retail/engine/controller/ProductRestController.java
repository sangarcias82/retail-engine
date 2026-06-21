package com.retail.engine.controller;

import com.retail.engine.dto.MessageResponse;
import com.retail.engine.dto.ProductRequest;
import com.retail.engine.dto.PurchaseRequest;
import com.retail.engine.model.Product;
import com.retail.engine.service.CsvImportResult;
import com.retail.engine.service.CsvImportService;
import com.retail.engine.service.ProductService;
import com.retail.engine.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Catalog, CRUD, CSV import, and purchase operations")
public class ProductRestController {

    private final ProductService productService;
    private final CsvImportService csvImportService;
    private final PurchaseService purchaseService;

    public ProductRestController(ProductService productService,
                                 CsvImportService csvImportService,
                                 PurchaseService purchaseService) {
        this.productService = productService;
        this.csvImportService = csvImportService;
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @Operation(summary = "List or search products", description = "Returns a paginated catalog, optionally filtered by name or SKU (case-insensitive partial match).")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    public Page<Product> listProducts(
            @Parameter(description = "Optional search term for name or SKU")
            @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "12") int size) {
        return productService.searchProducts(search, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found", content = @Content(schema = @Schema(implementation = com.retail.engine.dto.ErrorResponse.class)))
    })
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.retail.engine.dto.ValidationErrorResponse.class)))
    })
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product deleted"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public MessageResponse deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return new MessageResponse("Product deleted successfully!");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import products from CSV", description = "Upserts products by SKU with defensive data cleansing.")
    @ApiResponse(responseCode = "200", description = "Import completed with per-line error report")
    public CsvImportResult importProducts(
            @Parameter(description = "CSV file with columns: name, sku, description, category, price, stock, weight_kg")
            @RequestParam("file") MultipartFile file) {
        return csvImportService.importProducts(file);
    }

    @PostMapping("/purchase")
    @Operation(summary = "Purchase a product", description = "Simulates checkout: locks the product row for update, decrements stock with optimistic locking, and creates an order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase completed"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or concurrent conflict")
    })
    public MessageResponse purchase(@Valid @RequestBody PurchaseRequest request) {
        purchaseService.purchase(request.productId(), request.quantity());
        return new MessageResponse("Purchase completed successfully!");
    }
}
