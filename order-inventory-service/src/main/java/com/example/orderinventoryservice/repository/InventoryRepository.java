package com.example.orderinventoryservice.repository;

import com.example.orderinventoryservice.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findBySku(String sku);

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
