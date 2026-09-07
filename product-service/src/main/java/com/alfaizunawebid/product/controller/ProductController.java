package com.alfaizunawebid.product.controller;

import com.alfaizunawebid.product.dto.CreateProductRequest;
import com.alfaizunawebid.product.dto.ProductResponse;
import com.alfaizunawebid.product.dto.StockCheckResponse;
import com.alfaizunawebid.product.exception.AccessDeniedException;
import com.alfaizunawebid.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management and stock verification API")
public class ProductController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ProductService productService;

    /**
     * GET /api/v1/products
     * Public endpoint — browse all products with pagination.
     */
    @GetMapping
    @Operation(summary = "List all products", description = "Returns a paginated list of all available products. No authentication required.")
    @ApiResponse(responseCode = "200", description = "Product list retrieved successfully")
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    /**
     * GET /api/v1/products/{id}
     * Public endpoint — get product detail by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns a single product. No authentication required.")
    @ApiResponse(responseCode = "200", description = "Product found")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID") @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * POST /api/v1/products
     * Protected endpoint — requires ADMIN role (checked via X-User-Role header from Gateway).
     */
    @PostMapping
    @Operation(summary = "Create a new product", description = "Creates a product. Requires ADMIN role (JWT via API Gateway).")
    @ApiResponse(responseCode = "201", description = "Product created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    @ApiResponse(responseCode = "409", description = "Product with this SKU already exists")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest) {

        String userRole = (String) httpRequest.getAttribute("userRole");
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            log.warn("Unauthorized product creation attempt by role: {}", userRole);
            throw new AccessDeniedException("Only ADMIN role can create products");
        }

        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/products/{sku}/check-stock?quantity=N
     * Internal endpoint — used by order-service to verify stock before creating an order.
     * Requires a valid JWT (authenticated user context forwarded by Gateway).
     */
    @GetMapping("/{sku}/check-stock")
    @Operation(summary = "Check product stock by SKU",
            description = "Checks if sufficient stock is available for a given SKU and quantity. Used internally by other services.")
    @ApiResponse(responseCode = "200", description = "Stock check result")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<StockCheckResponse> checkStock(
            @Parameter(description = "Product SKU") @PathVariable String sku,
            @Parameter(description = "Requested quantity") @RequestParam @Min(1) int quantity) {
        return ResponseEntity.ok(productService.checkStock(sku, quantity));
    }
}
