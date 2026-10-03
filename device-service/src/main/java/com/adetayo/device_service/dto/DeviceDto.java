package com.adetayo.device_service.dto;


import com.adetayo.device_service.Model.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DeviceDto {
    private Long id;
    private String name;
    private String location;
    private DeviceType deviceType;
    private Long userId;
}
