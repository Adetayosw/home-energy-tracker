package com.adetayo.usage_service.dto;

import lombok.Builder;

@Builder
public record DeviceDto(
        Long id,
        String name,
        String location,
        Long userId,
        Double energyConsumed,
        String type
) {
}
