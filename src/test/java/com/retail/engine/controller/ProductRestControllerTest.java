package com.retail.engine.controller;

import com.retail.engine.controller.advice.GlobalExceptionHandler;
import com.retail.engine.dto.ProductRequest;
import com.retail.engine.model.Product;
import com.retail.engine.service.CsvImportResult;
import com.retail.engine.service.CsvImportService;
import com.retail.engine.service.ProductService;
import com.retail.engine.service.PurchaseException;
import com.retail.engine.service.PurchaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductRestController.class)
@Import(GlobalExceptionHandler.class)
class ProductRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private CsvImportService csvImportService;

    @MockBean
    private PurchaseService purchaseService;

    private Product sampleProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setSku("RS-001");
        product.setName("Running Shoes");
        product.setCategory("Footwear");
        product.setPrice(new BigDecimal("89.99"));
        product.setStock(10);
        product.setWeightKg(new BigDecimal("0.350"));
        return product;
    }

    private ProductRequest sampleRequest() {
        return new ProductRequest("RS-001", "Running Shoes", "Desc", "Footwear",
                new BigDecimal("89.99"), 10, new BigDecimal("0.350"));
    }

    @Test
    @DisplayName("Should list products with optional search")
    void shouldListProducts() throws Exception {
        when(productService.searchProducts("Mouse")).thenReturn(List.of(sampleProduct()));

        mockMvc.perform(get("/api/v1/products").param("search", "Mouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku", is("RS-001")));
    }

    @Test
    @DisplayName("Should return product by id")
    void shouldGetProductById() throws Exception {
        when(productService.getProduct(1L)).thenReturn(sampleProduct());

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Running Shoes")));
    }

    @Test
    @DisplayName("Should create product and return 201")
    void shouldCreateProduct() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(sampleProduct());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("RS-001")));

        verify(productService).createProduct(any(ProductRequest.class));
    }

    @Test
    @DisplayName("Should return 400 when create validation fails")
    void shouldReturnValidationErrorOnCreate() throws Exception {
        ProductRequest invalid = new ProductRequest("", "Name", null, "Cat",
                new BigDecimal("-1"), 10, new BigDecimal("1"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    @DisplayName("Should update product")
    void shouldUpdateProduct() throws Exception {
        when(productService.updateProduct(eq(1L), any(ProductRequest.class))).thenReturn(sampleProduct());

        mockMvc.perform(put("/api/v1/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku", is("RS-001")));
    }

    @Test
    @DisplayName("Should delete product")
    void shouldDeleteProduct() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Product deleted successfully!")));
    }

    @Test
    @DisplayName("Should import CSV file")
    void shouldImportCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv", "a,b".getBytes());
        when(csvImportService.importProducts(any())).thenReturn(new CsvImportResult(5, List.of("Line 2: error")));

        mockMvc.perform(multipart("/api/v1/products/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount", is(5)))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    @DisplayName("Should process purchase successfully")
    void shouldPurchaseProduct() throws Exception {
        doNothing().when(purchaseService).purchase(1L, 2);

        mockMvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Purchase completed successfully!")));
    }

    @Test
    @DisplayName("Should return 409 when purchase fails")
    void shouldReturnConflictWhenPurchaseFails() throws Exception {
        doThrow(new PurchaseException("Insufficient stock available."))
                .when(purchaseService).purchase(1L, 5);

        mockMvc.perform(post("/api/v1/products/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":1,"quantity":5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }
}
