package com.alfaizunawebid.product.service;

import com.alfaizunawebid.product.dto.CreateProductRequest;
import com.alfaizunawebid.product.dto.ProductResponse;
import com.alfaizunawebid.product.dto.StockCheckResponse;
import com.alfaizunawebid.product.exception.DuplicateSkuException;
import com.alfaizunawebid.product.exception.ProductNotFoundException;
import com.alfaizunawebid.product.model.Product;
import com.alfaizunawebid.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Returns a paginated list of all products.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(ProductResponse::from);
    }

    /**
     * Returns a single product by its ID.
     *
     * @throws ProductNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    /**
     * Creates a new product. Only callable by ADMIN role (enforced in controller).
     *
     * @throws DuplicateSkuException if SKU already exists
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created: id={}, sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    /**
     * Checks if a product has sufficient stock for a given quantity.
     * Used for internal service-to-service calls (e.g., order-service).
     *
     * @throws ProductNotFoundException if product with given SKU not found
     */
    @Transactional(readOnly = true)
    public StockCheckResponse checkStock(String sku, int requestedQuantity) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));

        boolean sufficient = product.getStock() >= requestedQuantity;
        String message = sufficient
                ? "Stock is sufficient for the requested quantity"
                : String.format("Insufficient stock. Available: %d, Requested: %d",
                product.getStock(), requestedQuantity);

        log.info("Stock check for SKU={}: available={}, requested={}, sufficient={}",
                sku, product.getStock(), requestedQuantity, sufficient);

        return StockCheckResponse.builder()
                .sku(sku)
                .availableStock(product.getStock())
                .requestedQuantity(requestedQuantity)
                .sufficient(sufficient)
                .message(message)
                .build();
    }
}
