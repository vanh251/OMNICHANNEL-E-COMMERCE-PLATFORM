package com.example.orderinventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    private List<@Valid OrderItemRequest> items;

    private String shippingAddress;

    private String note;
}
