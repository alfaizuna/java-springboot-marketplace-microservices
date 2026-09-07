package com.alfaizunawebid.product;

import com.alfaizunawebid.product.dto.StockCheckResponse;
import com.alfaizunawebid.product.exception.DuplicateSkuException;
import com.alfaizunawebid.product.exception.ProductNotFoundException;
import com.alfaizunawebid.product.model.Product;
import com.alfaizunawebid.product.repository.ProductRepository;
import com.alfaizunawebid.product.service.ProductService;
import com.alfaizunawebid.product.dto.CreateProductRequest;
import com.alfaizunawebid.product.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .sku("SKU-TEST-001")
                .name("Test Product")
                .description("A test product")
                .price(new BigDecimal("100000.00"))
                .stock(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getProductById - should return product when found")
    void getProductById_shouldReturnProduct_whenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse response = productService.getProductById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSku()).isEqualTo("SKU-TEST-001");
        assertThat(response.getName()).isEqualTo("Test Product");
        assertThat(response.getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("getProductById - should throw ProductNotFoundException when not found")
    void getProductById_shouldThrow_whenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("createProduct - should create product successfully")
    void createProduct_shouldCreateProduct_successfully() {
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-NEW-001");
        request.setName("New Product");
        request.setDescription("A new product");
        request.setPrice(new BigDecimal("250000.00"));
        request.setStock(100);

        when(productRepository.existsBySku("SKU-NEW-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p = Product.builder()
                    .id(2L).sku(p.getSku()).name(p.getName())
                    .description(p.getDescription()).price(p.getPrice())
                    .stock(p.getStock()).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build();
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getSku()).isEqualTo("SKU-NEW-001");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct - should throw DuplicateSkuException when SKU exists")
    void createProduct_shouldThrow_whenSkuAlreadyExists() {
        CreateProductRequest request = new CreateProductRequest();
        request.setSku("SKU-TEST-001");
        request.setName("Duplicate");
        request.setPrice(new BigDecimal("100.00"));
        request.setStock(10);

        when(productRepository.existsBySku("SKU-TEST-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-TEST-001");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkStock - should return sufficient=true when stock is enough")
    void checkStock_shouldReturnSufficient_whenStockIsEnough() {
        when(productRepository.findBySku("SKU-TEST-001")).thenReturn(Optional.of(sampleProduct));

        StockCheckResponse response = productService.checkStock("SKU-TEST-001", 10);

        assertThat(response.isSufficient()).isTrue();
        assertThat(response.getAvailableStock()).isEqualTo(50);
        assertThat(response.getRequestedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("checkStock - should return sufficient=false when stock is insufficient")
    void checkStock_shouldReturnInsufficient_whenStockIsLow() {
        when(productRepository.findBySku("SKU-TEST-001")).thenReturn(Optional.of(sampleProduct));

        StockCheckResponse response = productService.checkStock("SKU-TEST-001", 100);

        assertThat(response.isSufficient()).isFalse();
        assertThat(response.getMessage()).contains("Insufficient stock");
    }

    @Test
    @DisplayName("checkStock - should throw ProductNotFoundException when SKU not found")
    void checkStock_shouldThrow_whenSkuNotFound() {
        when(productRepository.findBySku("UNKNOWN-SKU")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.checkStock("UNKNOWN-SKU", 5))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("UNKNOWN-SKU");
    }
}
