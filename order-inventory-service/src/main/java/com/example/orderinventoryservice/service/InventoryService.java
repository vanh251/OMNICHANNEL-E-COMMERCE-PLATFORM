package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.entity.Inventory;
import com.example.orderinventoryservice.entity.InventoryReservation;
import com.example.orderinventoryservice.exception.InsufficientStockException;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional
    public void reserveStock(Long orderId, String sku, Integer quantity) {
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new InsufficientStockException("Product SKU not found in inventory: " + sku));

        if (inventory.getAvailableStock() < quantity) {
            log.warn("Insufficient stock for SKU {}. Available: {}, Requested: {}", 
                     sku, inventory.getAvailableStock(), quantity);
            throw new InsufficientStockException("Insufficient stock for SKU: " + sku);
        }

        // Update stock
        inventory.setReservedStock(inventory.getReservedStock() + quantity);
        inventory.setAvailableStock(inventory.getTotalStock() - inventory.getReservedStock());
        inventoryRepository.save(inventory);

        // Create reservation
        InventoryReservation reservation = InventoryReservation.builder()
                .sku(sku)
                .orderId(orderId)
                .quantity(quantity)
                .status(InventoryReservation.ReservationStatus.PENDING)
                .build();
        inventoryReservationRepository.save(reservation);

        log.info("Reserved {} items of SKU {} for orderId {}", quantity, sku, orderId);
    }

    @Transactional
    public void releaseStock(Long orderId) {
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(orderId);
        
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == InventoryReservation.ReservationStatus.PENDING) {
                Inventory inventory = inventoryRepository.findBySku(reservation.getSku()).orElse(null);
                if (inventory != null) {
                    inventory.setReservedStock(inventory.getReservedStock() - reservation.getQuantity());
                    inventory.setAvailableStock(inventory.getTotalStock() - inventory.getReservedStock());
                    inventoryRepository.save(inventory);
                }
                reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
                inventoryReservationRepository.save(reservation);
                log.info("Released {} items of SKU {} for orderId {}", reservation.getQuantity(), reservation.getSku(), orderId);
            }
        }
    }
}
