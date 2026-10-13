package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.event.EventPublisher;
import com.example.orderinventoryservice.event.InventorySyncEvent;
import com.example.orderinventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventorySyncServiceTest {
    @Mock InventoryRepository inventoryRepository;
    @Mock EventPublisher eventPublisher;
    @InjectMocks InventorySyncService service;

    @Test
    void createsStockOnceAndAcknowledgesEvent() {
        InventorySyncEvent event = event();
        when(inventoryRepository.findBySku("SKU-1")).thenReturn(Optional.empty());

        service.sync(event);

        org.mockito.ArgumentCaptor<Inventory> captor = org.mockito.ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo("product-1");
        assertThat(captor.getValue().getTotalStock()).isEqualTo(8);
        assertThat(captor.getValue().getReservedStock()).isZero();
        assertThat(captor.getValue().getAvailableStock()).isEqualTo(8);
        verify(eventPublisher).publishInventorySyncedEvent(any());
    }

    @Test
    void replayPreservesCurrentStock() {
        when(inventoryRepository.findBySku("SKU-1")).thenReturn(Optional.of(Inventory.builder()
                .productId("product-1").sku("SKU-1").totalStock(5)
                .reservedStock(2).availableStock(3).build()));

        service.sync(event());

        verify(inventoryRepository, never()).saveAndFlush(any());
        verify(eventPublisher).publishInventorySyncedEvent(any());
    }

    @Test
    void skuOwnedByAnotherProductFailsWithoutAcknowledgement() {
        when(inventoryRepository.findBySku("SKU-1")).thenReturn(Optional.of(Inventory.builder()
                .productId("another-product").sku("SKU-1").build()));
        assertThatThrownBy(() -> service.sync(event())).isInstanceOf(IllegalStateException.class);
        verify(eventPublisher, never()).publishInventorySyncedEvent(any());
    }

    private InventorySyncEvent event() {
        return new InventorySyncEvent("event-1", "ProductInventorySyncRequested", "product-1",
                List.of(new InventorySyncEvent.Variant("SKU-1", 8)));
    }
}
