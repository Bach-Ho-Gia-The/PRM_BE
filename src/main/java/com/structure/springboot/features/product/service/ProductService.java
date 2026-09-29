package com.structure.springboot.features.product.service;

import com.structure.springboot.features.product.dto.request.ProductRequest;
import com.structure.springboot.features.product.dto.response.ProductResponse;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(UUID id, ProductRequest request);
    void deleteProduct(UUID id);
    ProductResponse getProductById(UUID id);
    List<ProductResponse> getAllProducts();
}
