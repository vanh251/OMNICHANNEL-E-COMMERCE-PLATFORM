package com.example.orderinventoryservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdjustInventoryRequest(
        @NotNull(message = "Quantity delta is required")
        @Schema(description = "Non-zero signed quantity: positive to receive stock, negative to remove available stock",
                example = "20")
        Integer quantityDelta) {
}
