package com.example.productservice.service;

import com.example.productservice.dto.ProductRequest;
import com.example.productservice.dto.ProductVariantDTO;
import com.example.productservice.entity.Category;
import com.example.productservice.entity.Product;
import com.example.productservice.entity.ProductVariant;
import com.example.productservice.repository.CategoryRepository;
import com.example.productservice.repository.ProductRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProductInventorySyncTest {
    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks ProductService service;

    @Test
    void createdProductWaitsForInventoryAndCarriesPendingEvent() {
        when(categories.findById("cat")).thenReturn(Optional.of(Category.builder().id("cat").name("Phones").build()));
        when(products.findByVariantSku("SKU-1")).thenReturn(Optional.empty());
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        service.createProduct(request("SKU-1", 8));

        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(products).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isSkuIndexEligible()).isTrue();
        assertThat(saved.getInventorySyncEventId()).isNotBlank();
        assertThat(saved.isInventorySyncPublished()).isFalse();
        assertThat(saved.getVariants().get(0).getStock()).isEqualTo(8);
    }

    @Test
    void duplicateSkuInsideProductIsRejected() {
        when(categories.findById("cat")).thenReturn(Optional.of(Category.builder().id("cat").name("Phones").build()));
        ProductRequest request = request("SKU-1", 8);
        request.setVariants(List.of(request.getVariants().get(0), request.getVariants().get(0)));
        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate SKU");
    }

    @Test
    void addingVariantQueuesAnotherInventorySync() {
        Product existing = Product.builder().id("product-1").name("Phone").active(true)
                .variants(List.of(ProductVariant.builder().sku("SKU-1").stock(8).build()))
                .inventorySyncEventId("old-event").inventorySyncPublished(true).build();
        when(products.findById("product-1")).thenReturn(Optional.of(existing));
        when(categories.findById("cat")).thenReturn(Optional.of(Category.builder().id("cat").name("Phones").build()));
        when(products.findByVariantSku("SKU-1")).thenReturn(Optional.of(existing));
        when(products.findByVariantSku("SKU-2")).thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Product.class)))
                .thenReturn(existing);
        ProductRequest request = request("SKU-1", 8);
        request.setVariants(List.of(request.getVariants().get(0),
                ProductVariantDTO.builder().sku("SKU-2").name("White").price(BigDecimal.TEN).stock(5).build()));

        service.updateProduct("product-1", request);

        org.mockito.ArgumentCaptor<Update> changes = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), changes.capture(),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Product.class));
        org.bson.Document fields = changes.getValue().getUpdateObject().get("$set", org.bson.Document.class);
        assertThat(fields.getBoolean("active")).isFalse();
        assertThat(fields.getBoolean("inventorySyncPublished")).isFalse();
        assertThat(fields.getString("inventorySyncEventId")).isNotEqualTo("old-event");
    }

    @Test
    void changingExistingSkuStockThroughCatalogIsRejected() {
        Product existing = Product.builder().id("product-1")
                .variants(List.of(ProductVariant.builder().sku("SKU-1").stock(8).build())).build();
        when(products.findById("product-1")).thenReturn(Optional.of(existing));
        when(categories.findById("cat")).thenReturn(Optional.of(Category.builder().id("cat").name("Phones").build()));
        when(products.findByVariantSku("SKU-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateProduct("product-1", request("SKU-1", 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("managed by inventory");
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Product.class));
    }

    private ProductRequest request(String sku, int stock) {
        return ProductRequest.builder().name("Phone").categoryId("cat")
                .variants(List.of(ProductVariantDTO.builder().sku(sku).name("Black")
                        .price(BigDecimal.TEN).stock(stock).build())).build();
    }
}
