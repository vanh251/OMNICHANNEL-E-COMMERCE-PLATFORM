package com.example.orderinventoryservice.service;

import com.example.orderinventoryservice.dto.InventoryResponse;
import com.example.orderinventoryservice.entity.InventoryReservation;
import com.example.orderinventoryservice.exception.InsufficientStockException;
import com.example.orderinventoryservice.exception.InventoryNotFoundException;
import com.example.orderinventoryservice.exception.InvalidStockAdjustmentException;
import com.example.orderinventoryservice.mapper.InventoryMapper;
import com.example.orderinventoryservice.repository.InventoryRepository;
import com.example.orderinventoryservice.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional(readOnly = true)
    public Page<InventoryResponse> getInventory(String productId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("sku"));
        return (productId == null || productId.isBlank()
                ? inventoryRepository.findAll(pageable)
                : inventoryRepository.findByProductId(productId.trim(), pageable))
                .map(InventoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySku(String sku) {
        return InventoryMapper.toResponse(inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new InventoryNotFoundException(sku)));
    }

    @Transactional
    public InventoryResponse adjustStock(String sku, Integer quantityDelta, String changedBy) {
        if (quantityDelta == null || quantityDelta == 0) {
            throw new IllegalArgumentException("Quantity delta must be a non-zero integer");
        }
        // Bound before addition to avoid integer overflow and consuming reserved units.
        long minimumAvailable = Math.max(0L, -(long) quantityDelta);
        long maximumTotal = Integer.MAX_VALUE - Math.max(0L, (long) quantityDelta);
        if (inventoryRepository.adjustStock(sku, quantityDelta, minimumAvailable, maximumTotal) != 1) {
            if (inventoryRepository.findBySku(sku).isEmpty()) {
                throw new InventoryNotFoundException(sku);
            }
            throw new InvalidStockAdjustmentException(sku);
        }
        InventoryResponse response = getInventoryBySku(sku);
        log.info("Adjusted inventory sku={} quantityDelta={} changedBy={} totalStock={} availableStock={}",
                sku, quantityDelta, changedBy, response.totalStock(), response.availableStock());
        return response;
    }

    @Transactional
    public void reserveStock(Long orderId, String sku, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        // Persist the reservation before taking the hot SKU lock; failures roll back both writes.
        InventoryReservation reservation = InventoryReservation.builder()
                .sku(sku)
                .orderId(orderId)
                .quantity(quantity)
                .status(InventoryReservation.ReservationStatus.PENDING)
                .build();
        inventoryReservationRepository.save(reservation);

        // The conditional UPDATE takes the SQL row lock and checks stock atomically.
        if (inventoryRepository.reserveAvailableStock(sku, quantity) == 0) {
            throw new InsufficientStockException("Insufficient stock for SKU: " + sku);
        }

        log.debug("Reserved {} items of SKU {} for orderId {}", quantity, sku, orderId);
    }

    @Transactional
    public void releaseStock(Long orderId) {
        List<InventoryReservation> reservations = sortedReservations(orderId);
        
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == InventoryReservation.ReservationStatus.PENDING) {
                reservation.setStatus(InventoryReservation.ReservationStatus.RELEASED);
                inventoryReservationRepository.save(reservation);
                if (inventoryRepository.releaseReservedStock(reservation.getSku(), reservation.getQuantity()) != 1) {
                    throw new IllegalStateException("Cannot release reserved stock for " + reservation.getSku());
                }
                log.debug("Released {} items of SKU {} for orderId {}", reservation.getQuantity(), reservation.getSku(), orderId);
            }
        }
    }

    @Transactional
    public void confirmStock(Long orderId) {
        for (InventoryReservation reservation : sortedReservations(orderId)) {
            if (reservation.getStatus() == InventoryReservation.ReservationStatus.PENDING) {
                reservation.setStatus(InventoryReservation.ReservationStatus.CONFIRMED);
                inventoryReservationRepository.save(reservation);
                if (inventoryRepository.confirmReservedStock(reservation.getSku(), reservation.getQuantity()) != 1) {
                    throw new IllegalStateException("Cannot confirm reserved stock for " + reservation.getSku());
                }
            }
        }
    }

    private List<InventoryReservation> sortedReservations(Long orderId) {
        return inventoryReservationRepository.findByOrderId(orderId).stream()
                .sorted(Comparator.comparing(InventoryReservation::getSku)).toList();
    }
}
