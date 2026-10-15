package com.example.orderinventoryservice.controller;

import com.example.orderinventoryservice.dto.AdjustInventoryRequest;
import com.example.orderinventoryservice.dto.InventoryResponse;
import com.example.orderinventoryservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Inventory Controller", description = "Authoritative stock by SKU, managed by administrators")
@SecurityRequirement(name = "BearerAuth")
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "List inventory", description = "Optionally filter by productId; sorted by SKU ascending")
    public Page<InventoryResponse> getInventory(
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.getInventory(productId, page, size);
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get current stock for a SKU")
    public InventoryResponse getInventoryBySku(@PathVariable String sku) {
        return inventoryService.getInventoryBySku(sku);
    }

    @PostMapping("/{sku}/adjustments")
    @Operation(summary = "Adjust stock by a signed quantity",
            description = "Updates total and available stock atomically, preserving reserved stock. "
                    + "Returns 409 if stock would become negative or exceed the integer limit. "
                    + "Each successful request applies a new adjustment; do not retry automatically.")
    public InventoryResponse adjustStock(@PathVariable String sku,
                                         @Valid @RequestBody AdjustInventoryRequest request,
                                         Authentication authentication) {
        return inventoryService.adjustStock(sku, request.quantityDelta(), authentication.getName());
    }
}
