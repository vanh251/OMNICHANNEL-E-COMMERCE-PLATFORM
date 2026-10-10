package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.entity.InventoryReservation;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.repository.InventoryReservationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InventorySagaTest {
    @Test
    void confirmingReservationDeductsTotalOnce() {
        InventoryRepository inventoryRepository = mock(InventoryRepository.class);
        InventoryReservationRepository reservationRepository = mock(InventoryReservationRepository.class);
        InventoryService service = new InventoryService(inventoryRepository, reservationRepository);
        Inventory inventory = Inventory.builder().sku("SKU").totalStock(10)
                .reservedStock(2).availableStock(8).build();
        InventoryReservation reservation = InventoryReservation.builder().orderId(1L).sku("SKU")
                .quantity(2).status(InventoryReservation.ReservationStatus.PENDING).build();
        when(reservationRepository.findByOrderId(1L)).thenReturn(List.of(reservation));
        when(inventoryRepository.findBySkuForUpdate("SKU")).thenReturn(Optional.of(inventory));

        service.confirmStock(1L);
        service.confirmStock(1L);

        assertThat(inventory.getTotalStock()).isEqualTo(8);
        assertThat(inventory.getReservedStock()).isZero();
        assertThat(inventory.getAvailableStock()).isEqualTo(8);
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.ReservationStatus.CONFIRMED);
        verify(inventoryRepository).save(inventory);
    }
}
