package com.example.orderinventoryservice.repository;

import com.example.orderinventoryservice.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySku(String sku);

    Page<Inventory> findByProductId(String productId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.totalStock = i.totalStock + :quantityDelta,
                i.availableStock = i.availableStock + :quantityDelta,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.sku = :sku
                AND cast(i.availableStock as long) >= :minimumAvailable
                AND cast(i.totalStock as long) <= :maximumTotal
            """)
    int adjustStock(@Param("sku") String sku, @Param("quantityDelta") Integer quantityDelta,
                    @Param("minimumAvailable") long minimumAvailable,
                    @Param("maximumTotal") long maximumTotal);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.reservedStock = i.reservedStock + :quantity,
                i.availableStock = i.availableStock - :quantity,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.sku = :sku AND i.availableStock >= :quantity
            """)
    int reserveAvailableStock(@Param("sku") String sku, @Param("quantity") Integer quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.reservedStock = i.reservedStock - :quantity,
                i.availableStock = i.availableStock + :quantity,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.sku = :sku AND i.reservedStock >= :quantity
            """)
    int releaseReservedStock(@Param("sku") String sku, @Param("quantity") Integer quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.reservedStock = i.reservedStock - :quantity,
                i.totalStock = i.totalStock - :quantity,
                i.updatedAt = CURRENT_TIMESTAMP
            WHERE i.sku = :sku AND i.reservedStock >= :quantity AND i.totalStock >= :quantity
            """)
    int confirmReservedStock(@Param("sku") String sku, @Param("quantity") Integer quantity);
}
