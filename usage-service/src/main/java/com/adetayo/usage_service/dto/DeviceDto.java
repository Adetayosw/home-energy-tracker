package com.adetayo.usage_service.dto;

public record DeviceDto(
        Long id,
        String name,
        String location,
        Long userId
) {
}
