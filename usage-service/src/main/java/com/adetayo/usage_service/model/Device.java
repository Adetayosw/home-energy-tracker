package com.adetayo.usage_service.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    Long id;
    String name;
    String type;
    String location;
    double energyConsumed;
    Long userId;
}
